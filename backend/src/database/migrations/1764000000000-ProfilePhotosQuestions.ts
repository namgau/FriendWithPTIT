import { MigrationInterface, QueryRunner } from 'typeorm';

/** Additive: profile photo list and fun-question answers. */
export class ProfilePhotosQuestions1764000000000 implements MigrationInterface {
  name = 'ProfilePhotosQuestions1764000000000';

  async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`ALTER TABLE "profiles" ADD COLUMN IF NOT EXISTS "photos" text array NOT NULL DEFAULT '{}'`);
    await queryRunner.query(`CREATE TABLE IF NOT EXISTS "question_answers" (
      "id" uuid NOT NULL DEFAULT gen_random_uuid(),
      "user_id" character varying NOT NULL,
      "question_id" character varying(32) NOT NULL,
      "answer" integer NOT NULL,
      "acceptable" integer array NOT NULL DEFAULT '{}',
      "updated_at" TIMESTAMP NOT NULL DEFAULT now(),
      CONSTRAINT "PK_question_answers" PRIMARY KEY ("id"))`);
    await queryRunner.query(`CREATE UNIQUE INDEX IF NOT EXISTS "IDX_question_answers_user_question" ON "question_answers" ("user_id", "question_id")`);
    await queryRunner.query(`CREATE INDEX IF NOT EXISTS "IDX_question_answers_user" ON "question_answers" ("user_id")`);
  }

  async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE IF EXISTS "question_answers"`);
    await queryRunner.query(`ALTER TABLE "profiles" DROP COLUMN IF EXISTS "photos"`);
  }
}
