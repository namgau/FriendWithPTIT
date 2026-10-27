import { Controller, Get } from '@nestjs/common';
import { DataSource } from 'typeorm';

@Controller()
export class AppController {
  constructor(private readonly dataSource: DataSource) {}

  @Get()
  root() {
    return {
      service: 'fwp-backend',
      version: '0.1.0',
      docs: ['/healthz', '/auth/demo', '/profiles/discover', '/matches'],
    };
  }

  @Get('healthz')
  async health() {
    let database = 'down';
    try {
      await this.dataSource.query('SELECT 1');
      database = 'up';
    } catch {
      database = 'down';
    }
    return {
      status: database === 'up' ? 'ok' : 'degraded',
      service: 'fwp-backend',
      database,
      timestamp: new Date().toISOString(),
    };
  }
}
