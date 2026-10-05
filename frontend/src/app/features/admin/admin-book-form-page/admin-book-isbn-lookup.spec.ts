import { buildIsbnLookupApply, namesEqualTr } from './admin-book-isbn-lookup';

describe('buildIsbnLookupApply', () => {
  const authors = [{ id: 'a1', name: 'Ayşe Yılmaz' }];
  const publishers = [{ id: 'p1', name: 'Deniz Yayınları' }];

  it('selects matched author/publisher and fills fields', () => {
    const { patch, hints } = buildIsbnLookupApply(
      {
        isbn: '9780306406157',
        title: 'Kar',
        description: 'Açıklama',
        coverUrl: 'https://example.com/c.jpg',
        pageCount: 200,
        authors: ['Ayşe Yılmaz'],
        publishers: ['Deniz Yayınları'],
      },
      authors,
      publishers,
      '',
      namesEqualTr,
    );
    expect(patch['title']).toBe('Kar');
    expect(patch['authorIds']).toEqual(['a1']);
    expect(patch['publisherId']).toBe('p1');
    expect(hints).toEqual([]);
  });

  it('fills other fields and soft-hints unmatched author (no force)', () => {
    const { patch, hints } = buildIsbnLookupApply(
      {
        isbn: '9780306406157',
        title: 'Kar',
        authors: ['Bilinmeyen Yazar'],
        publishers: ['Deniz Yayınları'],
      },
      authors,
      publishers,
      '',
      namesEqualTr,
    );
    expect(patch['title']).toBe('Kar');
    expect(patch['authorIds']).toBeUndefined();
    expect(patch['publisherId']).toBe('p1');
    expect(hints.length).toBe(1);
    expect(hints[0]).toContain('Bilinmeyen Yazar');
    expect(hints[0]).toContain('Yazarlar');
  });

  it('does not touch authorIds when OL authors empty', () => {
    const { patch, hints } = buildIsbnLookupApply(
      {
        isbn: '9780306406157',
        title: 'Kar',
        authors: [],
        publishers: [],
      },
      authors,
      publishers,
      '978',
      namesEqualTr,
    );
    expect(patch['title']).toBe('Kar');
    expect(patch['authorIds']).toBeUndefined();
    expect(hints).toEqual([]);
  });
});
