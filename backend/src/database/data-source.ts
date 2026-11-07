import 'reflect-metadata';
import 'dotenv/config';
import { DataSource } from 'typeorm';
import { ENTITIES } from './entities';

export default new DataSource({
  type: 'postgres',
  url:
    process.env.DATABASE_URL ??
    'postgresql://fwp:fwp@localhost:5432/fwp',
  entities: ENTITIES,
  migrations: [`${__dirname}/migrations/*{.ts,.js}`],
  synchronize: false,
  logging: process.env.DB_LOGGING === 'true',
  ssl:
    process.env.DB_SSL === 'true'
      ? { rejectUnauthorized: false }
      : false,
});
