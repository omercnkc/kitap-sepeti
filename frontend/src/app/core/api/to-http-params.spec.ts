import { toHttpParams } from './to-http-params';

describe('toHttpParams', () => {
  it('should omit null, undefined and empty string', () => {
    const params = toHttpParams({
      q: 'kitap',
      page: 0,
      empty: '',
      missing: undefined,
      none: null,
      size: 20,
    });

    expect(params.keys().sort()).toEqual(['page', 'q', 'size']);
    expect(params.get('q')).toBe('kitap');
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('20');
  });
});
