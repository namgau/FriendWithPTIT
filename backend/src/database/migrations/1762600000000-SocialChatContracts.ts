import { MigrationInterface, QueryRunner } from 'typeorm';

/** Additive: legacy messages keep NULL keys and are never rewritten. */
export class SocialChatContracts1762600000000 implements MigrationInterface {
  name = 'SocialChatContracts1762600000000';

  async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`ALTER TABLE "messages" ADD COLUMN IF NOT EXISTS "client_message_id" uuid`);
    await queryRunner.query(`CREATE UNIQUE INDEX IF NOT EXISTS "IDX_messages_client_send" ON "messages" ("match_id", "sender_id", "client_message_id")`);
    await queryRunner.query(`CREATE INDEX IF NOT EXISTS "IDX_messages_history" ON "messages" ("match_id", "created_at", "id")`);
  }

  async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP INDEX IF EXISTS "IDX_messages_history"`);
    await queryRunner.query(`DROP INDEX IF EXISTS "IDX_messages_client_send"`);
    await queryRunner.query(`ALTER TABLE "messages" DROP COLUMN IF EXISTS "client_message_id"`);
  }
}
