import { buildCreateBookBody, buildUpdateBookBody, roundMoney2 } from './admin-book-form-body';

describe('admin book form body', () => {
  it('rounds money to 2 decimals', () => {
    expect(roundMoney2(19.999)).toBe(20);
    expect(roundMoney2(19.994)).toBe(19.99);
  });

  it('create omits blank isbn/cover and includes stock', () => {
    const body = buildCreateBookBody({
      title: 'Kar',
      priceAmount: 19.999,
      authorNames: [],
      categoryIds: [],
      coverUrl: '  ',
      description: '',
      isbn: '',
      pageCount: null,
      initialStock: 5,
    });
    expect(body.priceAmount).toBe(20);
    expect(body.isbn).toBeUndefined();
    expect(body.coverUrl).toBeUndefined();
    expect(body.initialStock).toBe(5);
    expect(Object.keys(body)).not.toContain('version');
  });

  it('update sends "" to clear isbn/cover and omits stock/status', () => {
    const body = buildUpdateBookBody({
      version: 3,
      title: 'Kar',
      priceAmount: 120.456,
      authorNames: ['Ayşe'],
      categoryIds: ['c1'],
      coverUrl: '',
      description: 'Açıklama',
      isbn: '',
      pageCount: 200,
    });

    expect(body.version).toBe(3);
    expect(body.priceAmount).toBe(120.46);
    expect(body.isbn).toBe('');
    expect(body.coverUrl).toBe('');
    expect(body.pageCount).toBe(200);
    expect(body.authorNames).toEqual(['Ayşe']);
    const keys = Object.keys(body);
    expect(keys).not.toContain('status');
    expect(keys).not.toContain('stockQuantity');
    expect(keys).not.toContain('initialStock');
  });

  it('update normalizes isbn with hyphens', () => {
    const body = buildUpdateBookBody({
      version: 1,
      title: 'T',
      priceAmount: 10,
      authorNames: [],
      categoryIds: [],
      coverUrl: 'https://example.com/x.jpg',
      description: '',
      isbn: '978-0-306-40615-7',
      pageCount: null,
    });
    expect(body.isbn).toBe('9780306406157');
    expect(body.coverUrl).toBe('https://example.com/x.jpg');
    expect(body.pageCount).toBeUndefined();
  });

  it('update omits isbn when unchanged from previous (incl. invalid seed)', () => {
    const body = buildUpdateBookBody(
      {
        version: 2,
        title: 'T',
        priceAmount: 10,
        authorNames: [],
        categoryIds: [],
        coverUrl: '',
        description: '',
        isbn: '9786050000011',
        pageCount: null,
      },
      { previousIsbn: '9786050000011' },
    );
    expect(Object.keys(body)).not.toContain('isbn');
  });

  it('update sends isbn when changed from previous', () => {
    const body = buildUpdateBookBody(
      {
        version: 2,
        title: 'T',
        priceAmount: 10,
        authorNames: [],
        categoryIds: [],
        coverUrl: '',
        description: '',
        isbn: '9780306406157',
        pageCount: null,
      },
      { previousIsbn: '9786050000011' },
    );
    expect(body.isbn).toBe('9780306406157');
  });
});
