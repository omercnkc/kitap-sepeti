import { AuthorResponse, IsbnMetadataResponse, PublisherResponse } from '../../../core/models';

export interface IsbnLookupApplyResult {
  patch: Record<string, unknown>;
  hints: string[];
}

/** Open Library metadata → form patch + soft hints (yazar create yok). */
export function buildIsbnLookupApply(
  meta: IsbnMetadataResponse,
  authors: Pick<AuthorResponse, 'id' | 'name'>[],
  publishers: Pick<PublisherResponse, 'id' | 'name'>[],
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
    patch['description'] = meta.description;
  }
  if (meta.coverUrl) {
    patch['coverUrl'] = meta.coverUrl;
  }
  if (meta.pageCount != null) {
    patch['pageCount'] = meta.pageCount;
  }

  const hints: string[] = [];
  const olAuthors = meta.authors ?? [];
  if (olAuthors.length) {
    const matchedAuthorIds: string[] = [];
    const unmatchedAuthors: string[] = [];
    for (const name of olAuthors) {
      const match = authors.find((a) => namesEqual(a.name, name));
      if (match) {
        matchedAuthorIds.push(match.id);
      } else {
        unmatchedAuthors.push(name);
      }
    }
    if (matchedAuthorIds.length) {
      patch['authorIds'] = matchedAuthorIds;
    }
    if (unmatchedAuthors.length) {
      hints.push(
        `OL yazar: ${unmatchedAuthors.join(', ')} (listede yok — sonra Yazarlar’dan eklenebilir)`,
      );
    }
  }

  const publisherName = (meta.publishers ?? [])[0];
  if (publisherName) {
    const pub = publishers.find((p) => namesEqual(p.name, publisherName));
    if (pub) {
      patch['publisherId'] = pub.id;
    } else {
      hints.push(
        `OL yayınevi: ${publisherName} (listede yok — sonra Yayınevleri’nden eklenebilir veya elle seçin)`,
      );
    }
  }

  return { patch, hints };
}

export function namesEqualTr(a: string, b: string): boolean {
  return a.trim().toLocaleLowerCase('tr-TR') === b.trim().toLocaleLowerCase('tr-TR');
}
