import { BadRequestException } from '@nestjs/common';
import sharp from 'sharp';

export type ImageKind = 'jpeg' | 'png' | 'webp' | 'gif';

export interface SanitizedImage {
  data: Buffer;
  kind: ImageKind;
  mimeType: string;
  extension: string;
  width: number;
  height: number;
}

export const IMAGE_MIME: Record<ImageKind, string> = {
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
  gif: 'image/gif',
};

export const IMAGE_EXTENSION: Record<ImageKind, string> = {
  jpeg: 'jpg',
  png: 'png',
  webp: 'webp',
  gif: 'gif',
};

/** Longest edge kept after re-encoding. Bigger inputs are scaled down. */
const MAX_EDGE = 2048;
/** Decompression-bomb guard applied to every frame. */
const MAX_INPUT_PIXELS = 40_000_000;
const MAX_ANIMATION_FRAMES = 120;
const MAX_ANIMATION_PIXELS = 120_000_000;

/** Identifies the format from the real bytes. The claimed MIME is never trusted. */
export function detectImageKind(bytes: Buffer): ImageKind | null {
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return 'jpeg';
  }
  if (
    bytes.length >= 8 &&
    bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))
  ) {
    return 'png';
  }
  if (bytes.length >= 6) {
    const head = bytes.subarray(0, 6).toString('latin1');
    if (head === 'GIF87a' || head === 'GIF89a') return 'gif';
  }
  if (
    bytes.length >= 12 &&
    bytes.subarray(0, 4).toString('latin1') === 'RIFF' &&
    bytes.subarray(8, 12).toString('latin1') === 'WEBP'
  ) {
    return 'webp';
  }
  return null;
}

/**
 * Decodes the image, applies EXIF orientation, then re-encodes it. Re-encoding
 * drops EXIF/GPS/XMP/IPTC/ICC and any trailing or embedded non-image payload.
 * Throws 400 for anything that is not a decodable JPEG, PNG, WebP or GIF.
 */
export async function sanitizeImage(bytes: Buffer): Promise<SanitizedImage> {
  const kind = detectImageKind(bytes);
  if (!kind) {
    throw new BadRequestException('File is not a supported JPEG, PNG, WebP or GIF image');
  }
  const animated = kind === 'gif' || kind === 'webp';
  try {
    const source = sharp(bytes, {
      failOn: 'error',
      limitInputPixels: MAX_INPUT_PIXELS,
      animated,
    });
    const meta = await source.metadata();
    const pages = meta.pages ?? 1;
    const frameWidth = meta.width ?? 0;
    const frameHeight = meta.pageHeight ?? meta.height ?? 0;
    if (!frameWidth || !frameHeight) {
      throw new BadRequestException('Image has no readable dimensions');
    }
    if (
      pages > MAX_ANIMATION_FRAMES ||
      frameWidth * frameHeight * pages > MAX_ANIMATION_PIXELS
    ) {
      throw new BadRequestException('Animated image is too large');
    }

    let pipeline = source
      .rotate()
      .resize({
        width: MAX_EDGE,
        height: MAX_EDGE,
        fit: 'inside',
        withoutEnlargement: true,
      });
    if (kind === 'jpeg') pipeline = pipeline.jpeg({ quality: 88 });
    else if (kind === 'png') pipeline = pipeline.png();
    else if (kind === 'webp') pipeline = pipeline.webp({ quality: 88 });
    else pipeline = pipeline.gif();

    const { data, info } = await pipeline.toBuffer({ resolveWithObject: true });
    return {
      data,
      kind,
      mimeType: IMAGE_MIME[kind],
      extension: IMAGE_EXTENSION[kind],
      width: info.width,
      height: info.pageHeight ?? info.height,
    };
  } catch (error) {
    if (error instanceof BadRequestException) throw error;
    throw new BadRequestException('Image is corrupted or cannot be processed');
  }
}
