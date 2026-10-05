import { UpdateBookRequest } from '../../../core/models';

/** Update gövdesinde stok/status olmamalı; version zorunlu. */
function buildUpdateBody(raw: {
  version: number;
  title: string;
  publisherId: string;
  priceAmount: number;
  authorIds: string[];
  categoryIds: string[];
  coverUrl: string;
  description: string;
  isbn: string;
  pageCount: number | null;
}): UpdateBookRequest {
  const body: UpdateBookRequest = {
    version: raw.version,
    title: raw.title.trim(),
    publisherId: raw.publisherId,
    priceAmount: raw.priceAmount,
    authorIds: raw.authorIds,
    categoryIds: raw.categoryIds,
    coverUrl: raw.coverUrl.trim(),
    description: raw.description.trim(),
    isbn: raw.isbn.trim(),
  };
  if (raw.pageCount != null) {
    body.pageCount = raw.pageCount;
  }
  return body;
}

describe('admin book update body', () => {
  it('includes version and omits stock/status', () => {
    const body = buildUpdateBody({
      version: 3,
      title: 'Kar',
      publisherId: 'p1',
      priceAmount: 120,
      authorIds: ['a1'],
      categoryIds: ['c1'],
      coverUrl: '',
      description: 'Açıklama',
      isbn: '978',
      pageCount: 200,
    });

    expect(body.version).toBe(3);
    expect(body.title).toBe('Kar');
    expect(body.pageCount).toBe(200);
    const keys = Object.keys(body);
    expect(keys).not.toContain('status');
    expect(keys).not.toContain('stockQuantity');
    expect(keys).not.toContain('initialStock');
  });
});
