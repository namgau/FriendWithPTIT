import { Profile } from '../database/entities';
import { maskedAvatarSeed, maskedIdentityId } from './anonymity';
import { distanceKm } from './matching';

export interface ProfileView {
  anonymous?: boolean;
  id: string;
  userId: string;
  nickname: string;
  fullName: string;
  age: number;
  gender: string;
  school: string;
  campus: string;
  faculty: string;
  major: string;
  academicYear: string;
  alumni: boolean;
  gpa?: number;
  gpaTier?: string;
  bio: string;
  interests: string[];
  avatarSeed: number;
  mode: string;
  distanceKm: number;
  verified: boolean;
  profileComplete: boolean;
  compatibility?: number;
  showGpaPublic: boolean;
  showLocationPublic: boolean;
}

/** Placeholder shown for the counterpart of a not-yet-revealed anonymous match. */
export function toAnonymousProfileView(matchId: string): ProfileView {
  const id = maskedIdentityId(matchId);
  return {
    anonymous: true,
    id,
    userId: id,
    nickname: 'Ẩn danh',
    fullName: 'Người ẩn danh',
    age: 0,
    gender: '',
    school: '',
    campus: '',
    faculty: '',
    major: '',
    academicYear: '',
    alumni: false,
    bio: '',
    interests: [],
    avatarSeed: maskedAvatarSeed(matchId),
    mode: '',
    distanceKm: 0,
    verified: false,
    profileComplete: true,
    showGpaPublic: false,
    showLocationPublic: false,
  };
}

export function toProfileView(
  profile: Profile,
  viewer?: Profile | null,
  compatibility?: number,
): ProfileView {
  const distance =
    viewer && profile.showLocationPublic
      ? distanceKm(viewer.lat, viewer.lng, profile.lat, profile.lng)
      : 0;
  return {
    id: profile.id,
    userId: profile.userId,
    nickname: profile.nickname,
    fullName: profile.fullName,
    age: profile.age,
    gender: profile.gender,
    school: profile.school,
    campus: profile.campus,
    faculty: profile.faculty,
    major: profile.major,
    academicYear: profile.academicYear,
    alumni: profile.alumni,
    ...(profile.showGpaPublic ? { gpa: profile.gpa, gpaTier: profile.gpaTier } : {}),
    bio: profile.bio,
    interests: profile.interests ?? [],
    avatarSeed: profile.avatarSeed,
    mode: profile.mode,
    distanceKm: distance,
    verified: profile.verified,
    profileComplete: profile.profileComplete,
    ...(compatibility === undefined ? {} : { compatibility }),
    showGpaPublic: profile.showGpaPublic,
    showLocationPublic: profile.showLocationPublic,
  };
}
