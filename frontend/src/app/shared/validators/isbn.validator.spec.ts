import { FormControl } from '@angular/forms';
import { isValidIsbnOptional, normalizeIsbn } from './isbn';
import { isbnValidator } from './isbn.validator';
import { httpUrlValidator } from './http-url.validator';

describe('isbn', () => {
  it('normalizes and validates checksums', () => {
    expect(normalizeIsbn('978-0-306-40615-7')).toBe('9780306406157');
    expect(isValidIsbnOptional('9780306406157')).toBeTrue();
    expect(isValidIsbnOptional('0-306-40615-2')).toBeTrue();
    expect(isValidIsbnOptional('')).toBeTrue();
    expect(isValidIsbnOptional('978')).toBeFalse();
    expect(isValidIsbnOptional('1234567890')).toBeFalse();
  });
});

describe('isbnValidator', () => {
  it('allows blank and rejects bad checksum', () => {
    const validate = isbnValidator();
    expect(validate(new FormControl(''))).toBeNull();
    expect(validate(new FormControl('9780306406157'))).toBeNull();
    expect(validate(new FormControl('978'))).toEqual({ isbn: true });
    expect(validate(new FormControl('9786050000011'))).toEqual({ isbn: true });
  });

  it('allows loaded legacy isbn until user changes it', () => {
    let loaded: string | null = '9786050000011';
    const validate = isbnValidator(() => loaded);
    expect(validate(new FormControl('9786050000011'))).toBeNull();
    expect(validate(new FormControl('978-605-000-001-1'))).toBeNull();
    expect(validate(new FormControl('978'))).toEqual({ isbn: true });
    expect(validate(new FormControl(''))).toBeNull();
    loaded = null;
    expect(validate(new FormControl('9786050000011'))).toEqual({ isbn: true });
  });
});

describe('httpUrlValidator', () => {
  const validate = httpUrlValidator();
  it('allows blank and absolute http(s) only', () => {
    expect(validate(new FormControl(''))).toBeNull();
    expect(validate(new FormControl('https://example.com/x.jpg'))).toBeNull();
    expect(validate(new FormControl('http://cdn.example.com/a.png'))).toBeNull();
    expect(validate(new FormControl('www.example.com'))).toEqual({ httpUrl: true });
    expect(validate(new FormControl('/relative.jpg'))).toEqual({ httpUrl: true });
    expect(validate(new FormControl('ftp://example.com/a'))).toEqual({ httpUrl: true });
  });
});
