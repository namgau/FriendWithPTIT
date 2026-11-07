import { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Creates the Campus Feed tables.
 *
 * Idempotent on purpose: databases that were first created with
 * DB_SYNCHRONIZE=true already contain these tables (with TypeORM-generated
 * index/constraint names). For every table that already exists this migration
 * does nothing, so adopting migrations on such a database never fails and never
 * touches existing rows.
 */
export class CampusFeed1762300000000 implements MigrationInterface {
  name = 'CampusFeed1762300000000';

  private async createTable(
    queryRunner: QueryRunner,
    table: string,
    statements: string[],
  ): Promise<void> {
    if (await queryRunner.hasTable(table)) return;
    for (const statement of statements) {
      await queryRunner.query(statement);
    }
  }

  public async up(queryRunner: QueryRunner): Promise<void> {
    await this.createTable(queryRunner, 'campus_authors', [
      `CREATE TABLE "campus_authors" (
        "id" uuid NOT NULL DEFAULT gen_random_uuid(),
        "user_id" uuid NOT NULL,
        "alias" character varying(48) NOT NULL,
        "avatar_seed" integer NOT NULL DEFAULT 1,
        "bio" text NOT NULL DEFAULT '',
        "show_faculty" boolean NOT NULL DEFAULT true,
        "show_campus" boolean NOT NULL DEFAULT true,
        "show_interests" boolean NOT NULL DEFAULT true,
        "discovery_enabled" boolean NOT NULL DEFAULT false,
        "created_at" TIMESTAMP NOT NULL DEFAULT now(),
        "updated_at" TIMESTAMP NOT NULL DEFAULT now(),
        CONSTRAINT "PK_campus_authors" PRIMARY KEY ("id"),
        CONSTRAINT "UQ_campus_authors_user_id" UNIQUE ("user_id"),
        CONSTRAINT "FK_campus_authors_user" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE
      )`,
      `CREATE UNIQUE INDEX "IDX_campus_authors_user_id" ON "campus_authors" ("user_id")`,
    ]);

    await this.createTable(queryRunner, 'campus_posts', [
      `CREATE TABLE "campus_posts" (
        "id" uuid NOT NULL DEFAULT gen_random_uuid(),
        "author_id" uuid NOT NULL,
        "content" text NOT NULL,
        "category" character varying(24) NOT NULL DEFAULT 'GENERAL',
        "anonymous_mode" character varying(24) NOT NULL DEFAULT 'FULLY_ANONYMOUS',
        "campus" character varying(100) NOT NULL DEFAULT '',
        "status" character varying(16) NOT NULL DEFAULT 'ACTIVE',
        "like_count" integer NOT NULL DEFAULT 0,
        "comment_count" integer NOT NULL DEFAULT 0,
        "bookmark_count" integer NOT NULL DEFAULT 0,
        "report_count" integer NOT NULL DEFAULT 0,
        "edited_at" TIMESTAMP WITH TIME ZONE,
        "created_at" TIMESTAMP NOT NULL DEFAULT now(),
        "updated_at" TIMESTAMP NOT NULL DEFAULT now(),
        CONSTRAINT "PK_campus_posts" PRIMARY KEY ("id"),
        CONSTRAINT "FK_campus_posts_author" FOREIGN KEY ("author_id") REFERENCES "campus_authors"("id") ON DELETE CASCADE
      )`,
      `CREATE INDEX "IDX_campus_posts_author_id" ON "campus_posts" ("author_id")`,
      `CREATE INDEX "IDX_campus_posts_status" ON "campus_posts" ("status")`,
      `CREATE INDEX "IDX_campus_posts_status_created_at" ON "campus_posts" ("status", "created_at")`,
      `CREATE INDEX "IDX_campus_posts_author_created_at" ON "campus_posts" ("author_id", "created_at")`,
    ]);

    await this.createTable(queryRunner, 'campus_post_media', [
      `CREATE TABLE "campus_post_media" (
        "id" uuid NOT NULL DEFAULT gen_random_uuid(),
        "post_id" uuid NOT NULL,
        "kind" character varying(16) NOT NULL DEFAULT 'IMAGE',
        "storage_key" character varying(120) NOT NULL,
        "mime_type" character varying(64) NOT NULL,
        "size_bytes" integer NOT NULL DEFAULT 0,
        "width" integer,
        "height" integer,
        "position" integer NOT NULL DEFAULT 0,
        "created_at" TIMESTAMP NOT NULL DEFAULT now(),
        CONSTRAINT "PK_campus_post_media" PRIMARY KEY ("id"),
        CONSTRAINT "FK_campus_post_media_post" FOREIGN KEY ("post_id") REFERENCES "campus_posts"("id") ON DELETE CASCADE
      )`,
      `CREATE INDEX "IDX_campus_post_media_post_id" ON "campus_post_media" ("post_id")`,
      `CREATE INDEX "IDX_campus_post_media_post_position" ON "campus_post_media" ("post_id", "position")`,
    ]);

    await this.createTable(queryRunner, 'campus_post_likes', [
      `CREATE TABLE "campus_post_likes" (
        "id" uuid NOT NULL DEFAULT gen_random_uuid(),
        "post_id" uuid NOT NULL,
        "user_id" uuid NOT NULL,
        "created_at" TIMESTAMP NOT NULL DEFAULT now(),
        CONSTRAINT "PK_campus_post_likes" PRIMARY KEY ("id"),
        CONSTRAINT "FK_campus_post_likes_post" FOREIGN KEY ("post_id") REFERENCES "campus_posts"("id") ON DELETE CASCADE,
        CONSTRAINT "FK_campus_post_likes_user" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE
      )`,
      `CREATE UNIQUE INDEX "IDX_campus_post_likes_post_user" ON "campus_post_likes" ("post_id", "user_id")`,
      `CREATE INDEX "IDX_campus_post_likes_user_created" ON "campus_post_likes" ("user_id", "created_at")`,
    ]);

    await this.createTable(queryRunner, 'campus_post_comments', [
      `CREATE TABLE "campus_post_comments" (
        "id" uuid NOT NULL DEFAULT gen_random_uuid(),
        "post_id" uuid NOT NULL,
        "author_id" uuid NOT NULL,
        "author_user_id" uuid NOT NULL,
        "content" text NOT NULL,
        "created_at" TIMESTAMP NOT NULL DEFAULT now(),
        CONSTRAINT "PK_campus_post_comments" PRIMARY KEY ("id"),
        CONSTRAINT "FK_campus_post_comments_post" FOREIGN KEY ("post_id") REFERENCES "campus_posts"("id") ON DELETE CASCADE,
        CONSTRAINT "FK_campus_post_comments_author" FOREIGN KEY ("author_id") REFERENCES "campus_authors"("id") ON DELETE CASCADE,
        CONSTRAINT "FK_campus_post_comments_user" FOREIGN KEY ("author_user_id") REFERENCES "users"("id") ON DELETE CASCADE
      )`,
      `CREATE INDEX "IDX_campus_post_comments_post_created" ON "campus_post_comments" ("post_id", "created_at")`,
      `CREATE INDEX "IDX_campus_post_comments_author_id" ON "campus_post_comments" ("author_id")`,
    ]);

    await this.createTable(queryRunner, 'campus_post_bookmarks', [
      `CREATE TABLE "campus_post_bookmarks" (
        "id" uuid NOT NULL DEFAULT gen_random_uuid(),
        "post_id" uuid NOT NULL,
        "user_id" uuid NOT NULL,
        "created_at" TIMESTAMP NOT NULL DEFAULT now(),
        CONSTRAINT "PK_campus_post_bookmarks" PRIMARY KEY ("id"),
        CONSTRAINT "FK_campus_post_bookmarks_post" FOREIGN KEY ("post_id") REFERENCES "campus_posts"("id") ON DELETE CASCADE,
        CONSTRAINT "FK_campus_post_bookmarks_user" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE CASCADE
      )`,
      `CREATE UNIQUE INDEX "IDX_campus_post_bookmarks_post_user" ON "campus_post_bookmarks" ("post_id", "user_id")`,
      `CREATE INDEX "IDX_campus_post_bookmarks_user_created" ON "campus_post_bookmarks" ("user_id", "created_at")`,
    ]);

    await this.createTable(queryRunner, 'campus_post_reports', [
      `CREATE TABLE "campus_post_reports" (
        "id" uuid NOT NULL DEFAULT gen_random_uuid(),
        "post_id" uuid NOT NULL,
        "reporter_id" uuid NOT NULL,
        "target_author_user_id" uuid NOT NULL,
        "reason" character varying(64) NOT NULL,
        "details" text NOT NULL DEFAULT '',
        "status" character varying(16) NOT NULL DEFAULT 'OPEN',
        "created_at" TIMESTAMP NOT NULL DEFAULT now(),
        CONSTRAINT "PK_campus_post_reports" PRIMARY KEY ("id"),
        CONSTRAINT "FK_campus_post_reports_post" FOREIGN KEY ("post_id") REFERENCES "campus_posts"("id") ON DELETE CASCADE,
        CONSTRAINT "FK_campus_post_reports_reporter" FOREIGN KEY ("reporter_id") REFERENCES "users"("id") ON DELETE CASCADE
      )`,
      `CREATE UNIQUE INDEX "IDX_campus_post_reports_post_reporter" ON "campus_post_reports" ("post_id", "reporter_id")`,
      `CREATE INDEX "IDX_campus_post_reports_post_id" ON "campus_post_reports" ("post_id")`,
      `CREATE INDEX "IDX_campus_post_reports_reporter_id" ON "campus_post_reports" ("reporter_id")`,
    ]);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    // Reverting the migration that created the tables drops them. Run this only
    // on databases whose Campus data may be discarded.
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_post_reports"`);
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_post_bookmarks"`);
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_post_comments"`);
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_post_likes"`);
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_post_media"`);
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_posts"`);
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_authors"`);
  }
}
