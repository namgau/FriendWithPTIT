import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHmac, randomUUID, timingSafeEqual } from 'crypto';
import { promises as fs } from 'fs';
import * as path from 'path';
import { sanitizeImage } from './campus-image';

const SAFE_FILE = /^[a-f0-9-]{36}\.(jpg|png|webp|gif)$/i;
const BASE64_BODY = /^[A-Za-z0-9+/\r\n]+={0,2}$/;
const DATA_URL_HEADER = /^data:image\/(?:jpeg|png|webp|gif);base64$/i;

export interface StoredCampusMedia {
  storageKey: string;
  mimeType: string;
  sizeBytes: number;
  width: number;
  height: number;
}

@Injectable()
export class CampusMediaStorage {
  private readonly directory: string;
  private readonly maxBytes: number;
  private readonly ttlSeconds: number;
  private readonly secret: string;

  constructor(config: ConfigService) {
    this.directory = path.resolve(
      config.get<string>('CAMPUS_UPLOAD_DIR', 'uploads/campus'),
    );
    this.maxBytes = Number.parseInt(
      config.get<string>('CAMPUS_MAX_IMAGE_BYTES', '4194304'),
      10,
    );
    this.ttlSeconds = Number.parseInt(
      config.get<string>('CAMPUS_MEDIA_URL_TTL_SECONDS', '86400'),
      10,
    );
    this.secret =
      config.get<string>('CAMPUS_MEDIA_SECRET') ||
      config.get<string>('JWT_SECRET', 'fwp-local-dev-secret');
  }

  /**
   * Validates the real file content, strips all metadata by re-encoding, and
   * stores the result under a random name. Client supplied MIME type and
   * dimensions are ignored.
   */
  async saveDataUrl(dataUrl: string): Promise<StoredCampusMedia> {
    const comma = dataUrl.indexOf(',');
    if (comma < 0 || !DATA_URL_HEADER.test(dataUrl.slice(0, comma).trim())) {
      throw new BadRequestException(
        'Image must be a base64 data URL using JPEG, PNG, WebP or GIF',
      );
    }
    const payload = dataUrl.slice(comma + 1).trim();
    // Reject oversized payloads before allocating the decoded buffer.
    if (Math.floor((payload.length * 3) / 4) > this.maxBytes + 4) {
      throw new BadRequestException('Image is too large');
    }
    if (!BASE64_BODY.test(payload)) {
      throw new BadRequestException('Image data is not valid base64');
    }
    const bytes = Buffer.from(payload, 'base64');
    if (!bytes.length) throw new BadRequestException('Image data is empty');
    if (bytes.length > this.maxBytes) {
      throw new BadRequestException('Image is too large');
    }

    const image = await sanitizeImage(bytes);
    if (image.data.length > this.maxBytes) {
      throw new BadRequestException('Image is too large');
    }
    await fs.mkdir(this.directory, { recursive: true });
    const storageKey = `${randomUUID()}.${image.extension}`;
    await fs.writeFile(path.join(this.directory, storageKey), image.data, {
      flag: 'wx',
      mode: 0o600,
    });
    return {
      storageKey,
      mimeType: image.mimeType,
      sizeBytes: image.data.length,
      width: image.width,
      height: image.height,
    };
  }

  async remove(storageKey: string): Promise<void> {
    if (!SAFE_FILE.test(storageKey)) return;
    await fs.rm(path.join(this.directory, storageKey), { force: true });
  }

  resolvePublicFile(file: string): string {
    if (!SAFE_FILE.test(file)) throw new NotFoundException('Media not found');
    const full = path.resolve(this.directory, file);
    if (!full.startsWith(`${this.directory}${path.sep}`)) {
      throw new NotFoundException('Media not found');
    }
    return full;
  }

  /**
   * Signed, expiring URL: possession of the link is the access grant, so image
   * loaders that cannot send a bearer token (Coil) keep working. `exp` snaps to
   * the hour, so the same URL repeats within an hour and image caches still hit.
   */
  urlFor = (storageKey: string): string => {
    const hour = 3600;
    const now = Math.floor(Date.now() / 1000);
    const exp = (Math.floor(now / hour) + 1) * hour + this.ttlSeconds - hour;
    return `/campus/media/${storageKey}?exp=${exp}&sig=${this.sign(storageKey, exp)}`;
  };

  verifySignature(file: string, exp: unknown, sig: unknown): boolean {
    if (!SAFE_FILE.test(file)) return false;
    if (typeof exp !== 'string' || typeof sig !== 'string') return false;
    if (!/^\d{1,12}$/.test(exp)) return false;
    const expiresAt = Number.parseInt(exp, 10);
    const now = Math.floor(Date.now() / 1000);
    if (expiresAt < now || expiresAt > now + this.ttlSeconds + 60) return false;
    const expected = Buffer.from(this.sign(file, expiresAt));
    const given = Buffer.from(sig);
    return expected.length === given.length && timingSafeEqual(expected, given);
  }

  private sign(file: string, exp: number): string {
    return createHmac('sha256', this.secret)
      .update(`campus-media:${file}:${exp}`)
      .digest('base64url');
  }
}
