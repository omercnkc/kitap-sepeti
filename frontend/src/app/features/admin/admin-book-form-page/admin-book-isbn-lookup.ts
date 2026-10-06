import { CategoryResponse, IsbnMetadataResponse } from '../../../core/models';

export interface IsbnLookupApplyResult {
  patch: Record<string, unknown>;
  hints: string[];
}

const DESCRIPTION_MAX = 10000;

/** OL subject (en) → bizim kategori adları (seed ile uyumlu). */
const SUBJECT_TO_CATEGORY_NAMES: ReadonlyArray<{ keys: string[]; names: string[] }> = [
  { keys: ['romance', 'fiction', 'novels', 'novel'], names: ['Roman', 'Edebiyat'] },
  { keys: ['science', 'popular science'], names: ['Bilim', 'Popüler Bilim'] },
  {
    keys: ['children', 'childrens', "children's", 'juvenile', 'graphic novel', 'picture book'],
    names: ['Çocuk'],
  },
];

/** Open Library metadata → form patch (yazar isimleri; sunucu find-or-create). */
export function buildIsbnLookupApply(
  meta: IsbnMetadataResponse,
  categories: Pick<CategoryResponse, 'id' | 'name' | 'slug'>[],
  currentIsbn: unknown,
  namesEqual: (a: string, b: string) => boolean,
): IsbnLookupApplyResult {
  const patch: Record<string, unknown> = {
    isbn: meta.isbn || currentIsbn,
  };
  if (meta.title) {
    patch['title'] = meta.title;
  }
  if (meta.description) {
    patch['description'] = truncateDescription(meta.description);
  }
  if (meta.coverUrl) {
    patch['coverUrl'] = meta.coverUrl;
  }
  if (meta.pageCount != null) {
    patch['pageCount'] = meta.pageCount;
  }

  const olAuthors = (meta.authors ?? [])
    .map((name) => String(name || '').trim())
    .filter((name) => name.length > 0);
  if (olAuthors.length) {
    patch['authorNames'] = olAuthors;
  }

  const matchedCategoryIds = matchCategoryIds(meta.subjects ?? [], categories, namesEqual);
  if (matchedCategoryIds.length) {
    patch['categoryIds'] = matchedCategoryIds;
  }

  return { patch, hints: [] };
}

export function namesEqualTr(a: string, b: string): boolean {
  return a.trim().toLocaleLowerCase('tr-TR') === b.trim().toLocaleLowerCase('tr-TR');
}

export function truncateDescription(value: string): string {
  const trimmed = value.trim();
  if (trimmed.length <= DESCRIPTION_MAX) {
    return trimmed;
  }
  return trimmed.slice(0, DESCRIPTION_MAX);
}

/** Sabit map + name/slug tr-TR eşitlik/contains; eşleşme yoksa []. */
export function matchCategoryIds(
  subjects: string[],
  categories: Pick<CategoryResponse, 'id' | 'name' | 'slug'>[],
  namesEqual: (a: string, b: string) => boolean,
): string[] {
  if (!subjects.length || !categories.length) {
    return [];
  }
  const targets = new Set<string>();
  for (const raw of subjects) {
    const subject = String(raw || '').trim();
    if (!subject) {
      continue;
    }
    const subjectKey = subject.toLocaleLowerCase('tr-TR');
    targets.add(subjectKey);
    for (const entry of SUBJECT_TO_CATEGORY_NAMES) {
      if (entry.keys.some((key) => subjectKey === key || subjectKey.includes(key))) {
        for (const name of entry.names) {
          targets.add(name.toLocaleLowerCase('tr-TR'));
        }
      }
    }
  }

  const ids: string[] = [];
  for (const category of categories) {
    const nameKey = category.name.trim().toLocaleLowerCase('tr-TR');
    const slugKey = category.slug.trim().toLocaleLowerCase('tr-TR');
    let matched = false;
    for (const target of targets) {
      if (
        namesEqual(category.name, target) ||
        namesEqual(category.slug, target) ||
        nameKey === target ||
        slugKey === target ||
        (target.length >= 4 && (nameKey.includes(target) || target.includes(nameKey))) ||
        (target.length >= 4 && (slugKey.includes(target) || target.includes(slugKey)))
      ) {
        matched = true;
        break;
      }
    }
    if (matched && !ids.includes(category.id)) {
      ids.push(category.id);
      if (ids.length >= 20) {
        break;
      }
    }
  }
  return ids;
}
