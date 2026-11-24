import { MigrationInterface, QueryRunner } from 'typeorm';

/** Additive: profile photo list and fun-question answers. */
export class ProfilePhotosQuestions1764000000000 implements MigrationInterface {
  name = 'ProfilePhotosQuestions1764000000000';

  async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`ALTER TABLE "profiles" ADD COLUMN IF NOT EXISTS "photos" text array NOT NULL DEFAULT '{}'`);
    await queryRunner.query(`ALTER TABLE "profiles" ADD COLUMN IF NOT EXISTS "cover_photo" text`);
    await queryRunner.query(`ALTER TABLE "profiles" ADD COLUMN IF NOT EXISTS "about" jsonb NOT NULL DEFAULT '{}'`);
    await queryRunner.query(`ALTER TABLE "profiles" ADD COLUMN IF NOT EXISTS "languages" text array NOT NULL DEFAULT '{}'`);
    await queryRunner.query(`ALTER TABLE "campus_posts" ADD COLUMN IF NOT EXISTS "community" character varying(40)`);
    await queryRunner.query(`CREATE TABLE IF NOT EXISTS "campus_communities" (
      "id" character varying(40) NOT NULL,
      "name" character varying(60) NOT NULL,
      "description" character varying(200) NOT NULL DEFAULT '',
      "owner_user_id" character varying,
      "requires_approval" boolean NOT NULL DEFAULT false,
      "hidden" boolean NOT NULL DEFAULT false,
      "created_at" TIMESTAMP NOT NULL DEFAULT now(),
      CONSTRAINT "PK_campus_communities" PRIMARY KEY ("id"))`);
    await queryRunner.query(`CREATE UNIQUE INDEX IF NOT EXISTS "IDX_campus_communities_name" ON "campus_communities" ("name")`);
    await queryRunner.query(`CREATE TABLE IF NOT EXISTS "campus_community_members" (
      "id" uuid NOT NULL DEFAULT gen_random_uuid(),
      "user_id" character varying NOT NULL,
      "community_id" character varying(40) NOT NULL,
      "status" character varying(16) NOT NULL DEFAULT 'MEMBER',
      "created_at" TIMESTAMP NOT NULL DEFAULT now(),
      CONSTRAINT "PK_campus_community_members" PRIMARY KEY ("id"))`);
    await queryRunner.query(`CREATE UNIQUE INDEX IF NOT EXISTS "IDX_campus_community_member" ON "campus_community_members" ("user_id", "community_id")`);
    await queryRunner.query(`CREATE TABLE IF NOT EXISTS "question_answers" (
      "id" uuid NOT NULL DEFAULT gen_random_uuid(),
      "user_id" character varying NOT NULL,
      "question_id" character varying(32) NOT NULL,
      "answer" integer NOT NULL,
      "acceptable" integer array NOT NULL DEFAULT '{}',
      "custom_text" character varying(200),
      "updated_at" TIMESTAMP NOT NULL DEFAULT now(),
      CONSTRAINT "PK_question_answers" PRIMARY KEY ("id"))`);
    await queryRunner.query(`CREATE UNIQUE INDEX IF NOT EXISTS "IDX_question_answers_user_question" ON "question_answers" ("user_id", "question_id")`);
    await queryRunner.query(`CREATE INDEX IF NOT EXISTS "IDX_question_answers_user" ON "question_answers" ("user_id")`);
  }

  async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "question_answers"`);
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_community_members"`);
    await queryRunner.query(`DROP TABLE IF EXISTS "campus_communities"`);
    await queryRunner.query(`ALTER TABLE "campus_posts" DROP COLUMN IF EXISTS "community"`);
    await queryRunner.query(`ALTER TABLE "profiles" DROP COLUMN IF EXISTS "cover_photo"`);
    await queryRunner.query(`ALTER TABLE "profiles" DROP COLUMN IF EXISTS "photos"`);
  }
}
