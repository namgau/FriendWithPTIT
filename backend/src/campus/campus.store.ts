import { Injectable } from '@nestjs/common';
import { DataSource, DeepPartial, EntityManager } from 'typeorm';
import { CampusPost, CampusPostMedia } from '../database/entities';

type Row = Record<string, any>;

/**
 * TypeORM returns `[rows, rowCount]` for UPDATE/DELETE and plain rows for
 * SELECT/INSERT. Every statement here ends in a SELECT or RETURNING, this
 * flattens both shapes into rows.
 */
export function toRows(result: unknown): Row[] {
  if (
    Array.isArray(result) &&
    result.length === 2 &&
    Array.isArray(result[0]) &&
    typeof result[1] === 'number'
  ) {
    return result[0] as Row[];
  }
  return Array.isArray(result) ? (result as Row[]) : [];
}

const FLAGS = {
  like: { table: 'campus_post_likes', counter: 'like_count' },
  bookmark: { table: 'campus_post_bookmarks', counter: 'bookmark_count' },
} as const;

export type FlagKind = keyof typeof FLAGS;

export interface SettingsPatch {
  discoveryEnabled?: boolean;
  discoveryDisabledByUser?: boolean;
  showFaculty?: boolean;
  showCampus?: boolean;
  showInterests?: boolean;
}

const SETTINGS_COLUMNS: Record<keyof SettingsPatch, string> = {
  discoveryEnabled: 'discovery_enabled',
  discoveryDisabledByUser: 'discovery_disabled_by_user',
  showFaculty: 'show_faculty',
  showCampus: 'show_campus',
  showInterests: 'show_interests',
};

/**
 * Every state change that also moves a counter is ONE statement (data-modifying
 * CTE). Duplicates and concurrent requests resolve in the database through the
 * unique indexes, so counters cannot drift and nothing surfaces as a 500.
 */
@Injectable()
export class CampusStore {
  constructor(private readonly dataSource: DataSource) {}

  async setFlag(
    kind: FlagKind,
    on: boolean,
    postId: string,
    userId: string,
  ): Promise<{ changed: boolean; count: number }> {
    const { table, counter } = FLAGS[kind];
    const sql = on
      ? `WITH ins AS (
           INSERT INTO ${table} (post_id, user_id)
           SELECT p.id, $2 FROM campus_posts p WHERE p.id = $1 AND p.status = 'ACTIVE'
           ON CONFLICT (post_id, user_id) DO NOTHING RETURNING 1
         ), upd AS (
           UPDATE campus_posts SET ${counter} = ${counter} + 1
           WHERE id = $1 AND EXISTS (SELECT 1 FROM ins) RETURNING ${counter} AS count
         )
         SELECT (SELECT count(*) FROM ins)::int AS changed,
                COALESCE((SELECT count FROM upd), (SELECT ${counter} FROM campus_posts WHERE id = $1), 0)::int AS count`
      : `WITH del AS (
           DELETE FROM ${table} WHERE post_id = $1 AND user_id = $2 RETURNING 1
         ), upd AS (
           UPDATE campus_posts SET ${counter} = GREATEST(${counter} - 1, 0)
           WHERE id = $1 AND EXISTS (SELECT 1 FROM del) RETURNING ${counter} AS count
         )
         SELECT (SELECT count(*) FROM del)::int AS changed,
                COALESCE((SELECT count FROM upd), (SELECT ${counter} FROM campus_posts WHERE id = $1), 0)::int AS count`;
    const [row] = toRows(await this.dataSource.query(sql, [postId, userId]));
    const changed = (row?.changed ?? 0) > 0;
    if (changed) return { changed, count: row?.count ?? 0 };
    // Nothing changed here, typically because a concurrent request won the
    // race. Inside one statement we only see the snapshot from before that
    // request committed, so read the counter again for an up-to-date value.
    const [fresh] = toRows(
      await this.dataSource.query(
        `SELECT ${counter} AS count FROM campus_posts WHERE id = $1`,
        [postId],
      ),
    );
    return { changed, count: fresh?.count ?? row?.count ?? 0 };
  }

  /** Which of these posts the user has liked / bookmarked. */
  async flaggedPostIds(
    kind: FlagKind,
    postIds: string[],
    userId: string,
  ): Promise<Set<string>> {
    if (!postIds.length) return new Set();
    const rows = toRows(
      await this.dataSource.query(
        `SELECT post_id FROM ${FLAGS[kind].table} WHERE user_id = $1 AND post_id = ANY($2::uuid[])`,
        [userId, postIds],
      ),
    );
    return new Set(rows.map((row) => row.post_id as string));
  }

  async deleteMedia(postId: string): Promise<void> {
    await this.dataSource.query(`DELETE FROM campus_post_media WHERE post_id = $1`, [
      postId,
    ]);
  }

  /** Post and media rows are created together or not at all. */
  async createPost(
    post: DeepPartial<CampusPost>,
    media: Array<DeepPartial<CampusPostMedia>>,
  ): Promise<CampusPost> {
    return this.dataSource.transaction(async (manager: EntityManager) => {
      const saved = await manager.save(manager.create(CampusPost, post));
      saved.media = media.length
        ? await manager.save(
            media.map((item) =>
              manager.create(CampusPostMedia, { ...item, postId: saved.id }),
            ),
          )
        : [];
      return saved;
    });
  }

  /** Returns the new comment id, or null when the post is no longer active. */
  async addComment(values: {
    postId: string;
    authorId: string;
    authorUserId: string;
    authorAlias: string;
    authorAvatarSeed: number;
    content: string;
  }): Promise<string | null> {
    const rows = toRows(
      await this.dataSource.query(
        `WITH ins AS (
           INSERT INTO campus_post_comments
             (post_id, author_id, author_user_id, author_alias, author_avatar_seed, content)
           SELECT p.id, $2, $3, $4, $5, $6 FROM campus_posts p
           WHERE p.id = $1 AND p.status = 'ACTIVE' RETURNING id
         ), upd AS (
           UPDATE campus_posts SET comment_count = comment_count + 1
           WHERE id = $1 AND EXISTS (SELECT 1 FROM ins) RETURNING 1
         )
         SELECT id FROM ins`,
        [
          values.postId,
          values.authorId,
          values.authorUserId,
          values.authorAlias,
          values.authorAvatarSeed,
          values.content,
        ],
      ),
    );
    return rows[0]?.id ?? null;
  }

  /**
   * Authors can only delete comments that are still ACTIVE. A comment under
   * review (or removed by a moderator) is moderation evidence: deleting it
   * would let its author dodge the decision, so it reads as "not found" until
   * a moderator restores it. Every deleted row was counted, so the counter
   * always drops by one.
   */
  async deleteComment(
    commentId: string,
    postId: string,
    authorUserId: string,
  ): Promise<boolean> {
    const [row] = toRows(
      await this.dataSource.query(
        `WITH del AS (
           DELETE FROM campus_post_comments
           WHERE id = $1 AND post_id = $2 AND author_user_id = $3 AND status = 'ACTIVE'
           RETURNING 1
         ), upd AS (
           UPDATE campus_posts SET comment_count = GREATEST(comment_count - 1, 0)
           WHERE id = $2 AND EXISTS (SELECT 1 FROM del) RETURNING 1
         )
         SELECT count(*)::int AS deleted FROM del`,
        [commentId, postId, authorUserId],
      ),
    );
    return (row?.deleted ?? 0) > 0;
  }

  async reportPost(
    postId: string,
    reporterId: string,
    targetAuthorUserId: string,
    reason: string,
    details: string,
    threshold: number,
  ): Promise<{ created: boolean; underReview: boolean }> {
    const [row] = toRows(
      await this.dataSource.query(
        `WITH ins AS (
           INSERT INTO campus_post_reports (post_id, reporter_id, target_author_user_id, reason, details)
           SELECT p.id, $2, $3, $4, $5 FROM campus_posts p
           WHERE p.id = $1 AND p.status = 'ACTIVE'
           ON CONFLICT (post_id, reporter_id) DO NOTHING RETURNING 1
         ), upd AS (
           UPDATE campus_posts SET report_count = report_count + 1,
             status = CASE WHEN status = 'ACTIVE' AND report_count + 1 >= $6 THEN 'UNDER_REVIEW' ELSE status END
           WHERE id = $1 AND EXISTS (SELECT 1 FROM ins) RETURNING status
         )
         SELECT (SELECT count(*) FROM ins)::int AS created,
                ((SELECT status FROM upd) = 'UNDER_REVIEW') AS under_review`,
        [postId, reporterId, targetAuthorUserId, reason, details, threshold],
      ),
    );
    return {
      created: (row?.created ?? 0) > 0,
      underReview: row?.under_review === true,
    };
  }

  async reportComment(
    commentId: string,
    postId: string,
    reporterId: string,
    reason: string,
    details: string,
    threshold: number,
  ): Promise<{ created: boolean; underReview: boolean }> {
    const [row] = toRows(
      await this.dataSource.query(
        `WITH ins AS (
           INSERT INTO campus_comment_reports (comment_id, reporter_id, target_author_user_id, reason, details)
           SELECT c.id, $3, c.author_user_id, $4, $5 FROM campus_post_comments c
           WHERE c.id = $1 AND c.post_id = $2 AND c.status = 'ACTIVE'
           ON CONFLICT (comment_id, reporter_id) DO NOTHING RETURNING 1
         ), upd AS (
           UPDATE campus_post_comments SET report_count = report_count + 1,
             status = CASE WHEN report_count + 1 >= $6 THEN 'UNDER_REVIEW' ELSE status END
           WHERE id = $1 AND status = 'ACTIVE' AND EXISTS (SELECT 1 FROM ins) RETURNING status
         ), dec AS (
           UPDATE campus_posts SET comment_count = GREATEST(comment_count - 1, 0)
           WHERE id = $2 AND EXISTS (SELECT 1 FROM upd WHERE status = 'UNDER_REVIEW') RETURNING 1
         )
         SELECT (SELECT count(*) FROM ins)::int AS created,
                EXISTS (SELECT 1 FROM upd WHERE status = 'UNDER_REVIEW') AS under_review`,
        [commentId, postId, reporterId, reason, details, threshold],
      ),
    );
    return {
      created: (row?.created ?? 0) > 0,
      underReview: row?.under_review === true,
    };
  }

  /** Moderator decision on a post that is UNDER_REVIEW. Null when not in review. */
  async decidePost(
    postId: string,
    action: 'RESTORE' | 'REMOVE',
    moderatorId: string,
  ): Promise<{ status: string; storageKeys: string[] } | null> {
    return this.dataSource.transaction(async (manager) => {
      const restore = action === 'RESTORE';
      const rows = toRows(
        await manager.query(
          restore
            ? `UPDATE campus_posts SET status = 'ACTIVE', report_count = 0,
                 moderated_by = $2, moderated_at = now()
               WHERE id = $1 AND status = 'UNDER_REVIEW' RETURNING status`
            : `UPDATE campus_posts SET status = 'REMOVED', discover_token = NULL,
                 moderated_by = $2, moderated_at = now()
               WHERE id = $1 AND status = 'UNDER_REVIEW' RETURNING status`,
          [postId, moderatorId],
        ),
      );
      if (!rows.length) return null;
      await manager.query(
        `UPDATE campus_post_reports SET status = $2 WHERE post_id = $1 AND status = 'OPEN'`,
        [postId, restore ? 'DISMISSED' : 'UPHELD'],
      );
      let storageKeys: string[] = [];
      if (!restore) {
        storageKeys = toRows(
          await manager.query(
            `DELETE FROM campus_post_media WHERE post_id = $1 RETURNING storage_key`,
            [postId],
          ),
        ).map((row) => row.storage_key as string);
      }
      return { status: rows[0].status as string, storageKeys };
    });
  }

  /**
   * Moderator decision on a comment that is UNDER_REVIEW. RESTORE clears the
   * report count (the reports were dismissed); REMOVE keeps it as the record
   * of how many people reported the comment.
   */
  async decideComment(
    commentId: string,
    action: 'RESTORE' | 'REMOVE',
  ): Promise<string | null> {
    return this.dataSource.transaction(async (manager) => {
      const restore = action === 'RESTORE';
      const rows = toRows(
        await manager.query(
          `UPDATE campus_post_comments
           SET status = $2, report_count = CASE WHEN $3::boolean THEN 0 ELSE report_count END
           WHERE id = $1 AND status = 'UNDER_REVIEW' RETURNING post_id, status`,
          [commentId, restore ? 'ACTIVE' : 'REMOVED', restore],
        ),
      );
      if (!rows.length) return null;
      if (restore) {
        // Hidden comments were taken out of the counter; give it back.
        await manager.query(
          `UPDATE campus_posts SET comment_count = comment_count + 1 WHERE id = $1`,
          [rows[0].post_id],
        );
      }
      await manager.query(
        `UPDATE campus_comment_reports SET status = $2 WHERE comment_id = $1 AND status = 'OPEN'`,
        [commentId, restore ? 'DISMISSED' : 'UPHELD'],
      );
      return rows[0].status as string;
    });
  }

  /**
   * Applies author settings in one transaction. Turning discovery off revokes
   * every live token; turning it on issues brand new ones, so a link seen
   * earlier can never come back to life.
   */
  async updateSettings(
    authorId: string,
    patch: SettingsPatch,
    campusForPosts: string | null,
  ): Promise<{ revoked: number; issued: number }> {
    return this.dataSource.transaction(async (manager) => {
      const sets: string[] = [];
      const params: unknown[] = [authorId];
      for (const key of Object.keys(SETTINGS_COLUMNS) as Array<keyof SettingsPatch>) {
        if (patch[key] === undefined) continue;
        params.push(patch[key]);
        sets.push(`${SETTINGS_COLUMNS[key]} = $${params.length}`);
      }
      if (sets.length) {
        await manager.query(
          `UPDATE campus_authors SET ${sets.join(', ')}, updated_at = now() WHERE id = $1`,
          params,
        );
      }
      let revoked = 0;
      let issued = 0;
      if (patch.discoveryEnabled === false) {
        revoked = toRows(
          await manager.query(
            `UPDATE campus_posts SET discover_token = NULL
             WHERE author_id = $1 AND discover_token IS NOT NULL RETURNING id`,
            [authorId],
          ),
        ).length;
      } else if (patch.discoveryEnabled === true) {
        issued = toRows(
          await manager.query(
            `UPDATE campus_posts SET discover_token = gen_random_uuid()
             WHERE author_id = $1 AND status = 'ACTIVE'
               AND anonymous_mode = 'DISCOVERABLE' AND discover_token IS NULL RETURNING id`,
            [authorId],
          ),
        ).length;
      }
      if (campusForPosts !== null) {
        await manager.query(`UPDATE campus_posts SET campus = $2 WHERE author_id = $1`, [
          authorId,
          campusForPosts,
        ]);
      }
      return { revoked, issued };
    });
  }
}
