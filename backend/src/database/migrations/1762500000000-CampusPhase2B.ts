import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Phase 2B additions. Purely additive and idempotent: safe on databases created
 * by migrations and on databases that DB_SYNCHRONIZE already extended.
 */
export class CampusPhase2B1762500000000 implements MigrationInterface {
  name = 'CampusPhase2B1762500000000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    // Author switched discovery off: posting must not silently re-enable it.
    await queryRunner.query(
      `ALTER TABLE "campus_authors" ADD COLUMN IF NOT EXISTS "discovery_disabled_by_user" boolean NOT NULL DEFAULT false`,
    );

    // Moderation audit trail (never exposed by public views).
    await queryRunner.query(
      `ALTER TABLE "campus_posts" ADD COLUMN IF NOT EXISTS "moderated_by" uuid`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_posts" ADD COLUMN IF NOT EXISTS "moderated_at" TIMESTAMP WITH TIME ZONE`,
    );

    // Comment reporting / moderation state.
    await queryRunner.query(
      `ALTER TABLE "campus_post_comments" ADD COLUMN IF NOT EXISTS "report_count" integer NOT NULL DEFAULT 0`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_post_comments" ADD COLUMN IF NOT EXISTS "status" character varying(16) NOT NULL DEFAULT 'ACTIVE'`,
    );

    if (!(await queryRunner.hasTable('campus_comment_reports'))) {
      await queryRunner.query(`
        CREATE TABLE "campus_comment_reports" (
          "id" uuid NOT NULL DEFAULT gen_random_uuid(),
          "comment_id" uuid NOT NULL,
          "reporter_id" uuid NOT NULL,
          "target_author_user_id" uuid NOT NULL,
          "reason" character varying(64) NOT NULL,
          "details" text NOT NULL DEFAULT '',
          "status" character varying(16) NOT NULL DEFAULT 'OPEN',
          "created_at" TIMESTAMP NOT NULL DEFAULT now(),
          CONSTRAINT "PK_campus_comment_reports" PRIMARY KEY ("id"),
          CONSTRAINT "FK_campus_comment_reports_comment" FOREIGN KEY ("comment_id") REFERENCES "campus_post_comments"("id") ON DELETE CASCADE,
          CONSTRAINT "FK_campus_comment_reports_reporter" FOREIGN KEY ("reporter_id") REFERENCES "users"("id") ON DELETE CASCADE
        )
      `);
      await queryRunner.query(
        `CREATE UNIQUE INDEX "IDX_campus_comment_reports_comment_reporter" ON "campus_comment_reports" ("comment_id", "reporter_id")`,
      );
      await queryRunner.query(
        `CREATE INDEX "IDX_campus_comment_reports_comment_id" ON "campus_comment_reports" ("comment_id")`,
      );
      await queryRunner.query(
        `CREATE INDEX "IDX_campus_comment_reports_reporter_id" ON "campus_comment_reports" ("reporter_id")`,
      );
    }
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_comment_reports"`);
    await queryRunner.query(
      `ALTER TABLE "campus_post_comments" DROP COLUMN IF EXISTS "status"`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_post_comments" DROP COLUMN IF EXISTS "report_count"`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_posts" DROP COLUMN IF EXISTS "moderated_at"`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_posts" DROP COLUMN IF EXISTS "moderated_by"`,
    );
    await queryRunner.query(
      `ALTER TABLE "campus_authors" DROP COLUMN IF EXISTS "discovery_disabled_by_user"`,
    );
  }
}
