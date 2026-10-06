import { buildIsbnLookupApply, matchCategoryIds, namesEqualTr, truncateDescription } from './admin-book-isbn-lookup';

describe('buildIsbnLookupApply', () => {
  const categories = [
    { id: 'c-edebiyat', name: 'Edebiyat', slug: 'edebiyat' },
    { id: 'c-roman', name: 'Roman', slug: 'roman' },
    { id: 'c-bilim', name: 'Bilim', slug: 'bilim' },
    { id: 'c-pop', name: 'Popüler Bilim', slug: 'populer-bilim' },
    { id: 'c-cocuk', name: 'Çocuk', slug: 'cocuk' },
  ];

  it('writes author names from OL metadata', () => {
    const { patch, hints } = buildIsbnLookupApply(
      {
        isbn: '9788467520446',
        title: 'Hugo',
        description: 'ORPHAN, CLOCK KEEPER, THIEF.',
        coverUrl: 'https://example.com/c.jpg',
        pageCount: 200,
        authors: ['Brian Selznick'],
        subjects: [],
      },
      categories,
      '',
      namesEqualTr,
    );
    expect(patch['title']).toBe('Hugo');
    expect(patch['description']).toBe('ORPHAN, CLOCK KEEPER, THIEF.');
    expect(patch['authorNames']).toEqual(['Brian Selznick']);
    expect(patch['categoryIds']).toBeUndefined();
    expect(hints).toEqual([]);
  });

  it('maps unknown author names from OL', () => {
    const { patch, hints } = buildIsbnLookupApply(
      {
        isbn: '9780306406157',
        title: 'Kar',
        authors: ['Bilinmeyen Yazar'],
        subjects: [],
      },
      categories,
      '',
      namesEqualTr,
    );
    expect(patch['authorNames']).toEqual(['Bilinmeyen Yazar']);
    expect(hints).toEqual([]);
  });

  it('does not touch authorNames when OL authors empty', () => {
    const { patch } = buildIsbnLookupApply(
      {
        isbn: '9780306406157',
        title: 'Kar',
        authors: [],
        subjects: [],
      },
      categories,
      '978',
      namesEqualTr,
    );
    expect(patch['title']).toBe('Kar');
    expect(patch['authorNames']).toBeUndefined();
  });

  it('maps juvenile/children subjects to Çocuk; truncates description', () => {
    const longDesc = 'x'.repeat(10050);
    const { patch } = buildIsbnLookupApply(
      {
        isbn: '9788467520446',
        title: 'Hugo',
        description: longDesc,
        authors: ['Brian Selznick'],
        subjects: ['Juvenile fiction', 'Children', 'Picture books'],
      },
      categories,
      '',
      namesEqualTr,
    );
    expect(patch['description']).toBe('x'.repeat(10000));
    expect(patch['categoryIds']).toContain('c-cocuk');
  });

  it('does not set categoryIds when subjects do not match', () => {
    const { patch } = buildIsbnLookupApply(
      {
        isbn: '9780306406157',
        title: 'Kar',
        authors: [],
        subjects: ['Utterly Obscure Topic'],
      },
      categories,
      '',
      namesEqualTr,
    );
    expect(patch['categoryIds']).toBeUndefined();
  });
});

describe('matchCategoryIds / truncateDescription', () => {
  it('maps graphic novel and picture book to Çocuk', () => {
    const categories = [{ id: 'c-cocuk', name: 'Çocuk', slug: 'cocuk' }];
    expect(matchCategoryIds(['Graphic novels'], categories, namesEqualTr)).toEqual(['c-cocuk']);
    expect(matchCategoryIds(['Picture book'], categories, namesEqualTr)).toEqual(['c-cocuk']);
  });

  it('truncates at 10000', () => {
    expect(truncateDescription('  short  ')).toBe('short');
    expect(truncateDescription('y'.repeat(10001)).length).toBe(10000);
  });
});
