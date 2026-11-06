import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Additive privacy hardening for Campus Feed. No column or row is dropped.
 * Every statement is idempotent so it is safe on databases where
 * DB_SYNCHRONIZE already created the new columns.
 */
export class CampusAnonymityHardening1762400000000
  implements MigrationInterface
{
  name = 'CampusAnonymityHardening1762400000000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    // Anonymous origin of swipes and matches.
    await queryRunner.query(
      `ALTER TABLE "swipes" ADD COLUMN IF NOT EXISTS "anonymous" boolean NOT NULL DEFAULT false`,
    );
    await queryRunner.query(
      `ALTER TABLE "matches" ADD COLUMN IF NOT EXISTS "anonymous" boolean NOT NULL DEFAULT false`,
    );

    // Per-post persona and per-post opaque discover token.
    await queryRunner.query(
      `ALTER TABLE "campus_posts" ADD COLUMN IF NOT EXISTS "author_alias" character varying(48) NOT NULL DEFAULT ''`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_posts" ADD COLUMN IF NOT EXISTS "author_avatar_seed" integer NOT NULL DEFAULT 1`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_posts" ADD COLUMN IF NOT EXISTS "discover_token" uuid`,
    );
    await queryRunner.query(
      `CREATE UNIQUE INDEX IF NOT EXISTS "IDX_campus_posts_discover_token" ON "campus_posts" ("discover_token")`,
    );

    // Per-thread persona on comments.
    await queryRunner.query(
      `ALTER TABLE "campus_post_comments" ADD COLUMN IF NOT EXISTS "author_alias" character varying(48) NOT NULL DEFAULT ''`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_post_comments" ADD COLUMN IF NOT EXISTS "author_avatar_seed" integer NOT NULL DEFAULT 1`,
    );

    // Disclosure flags now default to hidden (explicit opt-in).
    await queryRunner.query(
      `ALTER TABLE "campus_authors" ALTER COLUMN "show_faculty" SET DEFAULT false`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_authors" ALTER COLUMN "show_campus" SET DEFAULT false`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_authors" ALTER COLUMN "show_interests" SET DEFAULT false`,
    );

    // Backfill existing posts with an unlinkable persona. Only rows that have
    // not been migrated yet are touched.
    await queryRunner.query(`
      UPDATE "campus_posts"
      SET "author_alias" = 'Ẩn danh #' || (1000 + floor(random() * 9000))::int,
          "author_avatar_seed" = 1 + floor(random() * 20)::int
      WHERE "author_alias" = ''
    `);
    // Old tokens were the account-level author id and are intentionally
    // invalidated. Only live discoverable posts get a fresh per-post token.
    await queryRunner.query(`
      UPDATE "campus_posts" p
      SET "discover_token" = gen_random_uuid()
      FROM "campus_authors" a
      WHERE a."id" = p."author_id"
        AND a."discovery_enabled" = true
        AND p."anonymous_mode" = 'DISCOVERABLE'
        AND p."status" = 'ACTIVE'
        AND p."discover_token" IS NULL
    `);
    // The post owner keeps the post persona inside their own thread.
    await queryRunner.query(`
      UPDATE "campus_post_comments" c
      SET "author_alias" = p."author_alias",
          "author_avatar_seed" = p."author_avatar_seed"
      FROM "campus_posts" p
      WHERE p."id" = c."post_id"
        AND p."author_id" = c."author_id"
        AND c."author_alias" = ''
    `);
    // Other commenters: stable within a thread, unrelated across threads.
    await queryRunner.query(`
      UPDATE "campus_post_comments"
      SET "author_alias" = 'Ẩn danh #' || (1000 + (('x' || substr(md5("post_id"::text || "author_user_id"::text), 1, 6))::bit(24)::int % 9000)),
          "author_avatar_seed" = 1 + (('x' || substr(md5("author_user_id"::text || "post_id"::text), 7, 4))::bit(16)::int % 20)
      WHERE "author_alias" = ''
    `);

    // No API ever let authors choose these flags, so the old "true" defaults
    // were never a consent. Reset to hidden. Stored post.campus values are
    // kept untouched; the view masks them unless show_campus is true.
    await queryRunner.query(`
      UPDATE "campus_authors"
      SET "show_faculty" = false, "show_campus" = false, "show_interests" = false
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `DROP INDEX IF EXISTS "IDX_campus_posts_discover_token"`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_post_comments" DROP COLUMN IF EXISTS "author_avatar_seed"`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_post_comments" DROP COLUMN IF EXISTS "author_alias"`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_posts" DROP COLUMN IF EXISTS "discover_token"`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_posts" DROP COLUMN IF EXISTS "author_avatar_seed"`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_posts" DROP COLUMN IF EXISTS "author_alias"`,
    );
    await queryRunner.query(`ALTER TABLE "matches" DROP COLUMN IF EXISTS "anonymous"`);
    await queryRunner.query(`ALTER TABLE "swipes" DROP COLUMN IF EXISTS "anonymous"`);
    await queryRunner.query(
      `ALTER TABLE "campus_authors" ALTER COLUMN "show_interests" SET DEFAULT true`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_authors" ALTER COLUMN "show_campus" SET DEFAULT true`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_authors" ALTER COLUMN "show_faculty" SET DEFAULT true`,
    );
  }
}
