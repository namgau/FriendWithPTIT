import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Match, Message, Notification, User } from '../database/entities';

/** Tài khoản nhân vật demo trả lời bằng Gemini (tạo trong SeedService). */
export const BOT_EMAIL = 'mo@demo.fwp.local';

const PERSONA = `Bạn là "Phùng Thanh Mộ", 22 tuổi, sinh viên năm 4 ngành Công nghệ Đa phương tiện ở PTIT — một nhân vật
hư cấu trong bản demo app hẹn hò FwP. Hai người vừa match và đang nhắn tin làm quen.
Tính cách: vui tính, hay trêu nhẹ, mê game, bóng đá và trà sữa, quan tâm người đối diện.
Cách nhắn: tiếng Việt tự nhiên kiểu sinh viên, 1–2 câu ngắn mỗi lượt, thỉnh thoảng một emoji, hay hỏi lại để giữ chuyện.
Giới hạn: không xin số điện thoại, địa chỉ hay ảnh riêng ; không nội dung người lớn ; nếu được hỏi thẳng có phải AI/bot
không thì nói thật đây là nhân vật demo do AI trả lời.`;

const FALLBACK = 'Mộ đang bận xíu, tí nữa rep nha 😅';

@Injectable()
export class BotService {
  private readonly logger = new Logger(BotService.name);
  private botUserId: string | null | undefined;

  constructor(
    @InjectRepository(User) private readonly users: Repository<User>,
    @InjectRepository(Message) private readonly messages: Repository<Message>,
    @InjectRepository(Match) private readonly matches: Repository<Match>,
    @InjectRepository(Notification) private readonly notifications: Repository<Notification>,
    private readonly config: ConfigService,
  ) {}

  async isBot(userId: string): Promise<boolean> {
    if (this.botUserId === undefined) {
      this.botUserId = (await this.users.findOne({ where: { email: BOT_EMAIL } }))?.id ?? null;
    }
    return this.botUserId === userId;
  }

  /** Gọi sau khi người dùng nhắn cho bot: trả lời sau ~1,5 giây, không chặn request gửi tin. */
  scheduleReply(matchId: string, humanUserId: string, botUserId: string): void {
    setTimeout(() => {
      this.reply(matchId, humanUserId, botUserId).catch((error) =>
        this.logger.warn(`Bot reply failed: ${error instanceof Error ? error.message : error}`));
    }, 1500);
  }

  private async reply(matchId: string, humanUserId: string, botUserId: string) {
    const history = (await this.messages.find({ where: { matchId }, order: { createdAt: 'DESC' }, take: 20 })).reverse();
    const text = (await this.generate(history.map((m) => ({ mine: m.senderId === botUserId, text: m.text })))) || FALLBACK;
    const message = await this.messages.save(this.messages.create({ matchId, senderId: botUserId, text, type: 'TEXT', meta: null }));
    await this.matches.update({ id: matchId }, { lastMessageAt: message.createdAt });
    await this.notifications.save(this.notifications.create({
      userId: humanUserId, kind: 'MESSAGE', title: 'Phùng Thanh Mộ', body: text.slice(0, 160), data: { matchId },
    }));
  }

  /** Có XAI_API_KEY thì dùng Grok, không thì Gemini ; '' khi chưa có key hoặc lỗi (dùng câu dự phòng). */
  private async generate(turns: Array<{ mine: boolean; text: string }>): Promise<string> {
    const xai = this.config.get<string>('XAI_API_KEY', '').trim();
    if (xai) return this.generateGrok(xai, turns);
    const key = this.config.get<string>('GEMINI_API_KEY', '').trim();
    if (!key) return '';
    const model = this.config.get<string>('GEMINI_MODEL', 'gemini-2.5-flash');
    // Gemini cần lượt đầu là user và gộp các lượt liên tiếp cùng vai.
    const contents: Array<{ role: string; parts: Array<{ text: string }> }> = [];
    for (const t of turns) {
      const role = t.mine ? 'model' : 'user';
      const last = contents[contents.length - 1];
      if (last?.role === role) last.parts[0].text += `\n${t.text}`;
      else contents.push({ role, parts: [{ text: t.text }] });
    }
    while (contents[0]?.role === 'model') contents.shift();
    if (!contents.length) return '';
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': key },
      body: JSON.stringify({
        system_instruction: { parts: [{ text: PERSONA }] },
        contents,
        generationConfig: { temperature: 0.9, maxOutputTokens: 256, thinkingConfig: { thinkingBudget: 0 } },
      }),
      signal: AbortSignal.timeout(20_000),
    });
    if (!response.ok) {
      this.logger.warn(`Gemini ${response.status}: ${(await response.text()).slice(0, 200)}`);
      return '';
    }
    const data = (await response.json()) as { candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }> };
    return (data.candidates?.[0]?.content?.parts ?? []).map((p) => p.text ?? '').join('').trim().slice(0, 1000);
  }

  /** xAI Grok, API dạng OpenAI chat completions. */
  private async generateGrok(key: string, turns: Array<{ mine: boolean; text: string }>): Promise<string> {
    const model = this.config.get<string>('XAI_MODEL', 'grok-3-mini');
    const response = await fetch('https://api.x.ai/v1/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
      body: JSON.stringify({
        model,
        temperature: 0.9,
        max_tokens: 400,
        messages: [
          { role: 'system', content: PERSONA },
          ...turns.map((t) => ({ role: t.mine ? 'assistant' : 'user', content: t.text })),
        ],
      }),
      signal: AbortSignal.timeout(30_000),
    });
    if (!response.ok) {
      this.logger.warn(`Grok ${response.status}: ${(await response.text()).slice(0, 200)}`);
      return '';
    }
    const data = (await response.json()) as { choices?: Array<{ message?: { content?: string } }> };
    return (data.choices?.[0]?.message?.content ?? '').trim().slice(0, 1000);
  }
}
