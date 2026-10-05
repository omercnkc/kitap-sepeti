import { FormControl } from '@angular/forms';
import { filterTrPhoneInput, isValidTrPhone, normalizeTrPhone } from './tr-phone';
import { trPhoneValidator } from './tr-phone.validator';

describe('tr-phone', () => {
  it('normalizes accepted TR mobile formats to 5xxxxxxxxx', () => {
    expect(normalizeTrPhone('5551234567')).toBe('5551234567');
    expect(normalizeTrPhone('05551234567')).toBe('5551234567');
    expect(normalizeTrPhone('+905551234567')).toBe('5551234567');
    expect(normalizeTrPhone('90 555 123 45 67')).toBe('5551234567');
    expect(normalizeTrPhone(' 555 123 45 67 ')).toBe('5551234567');
  });

  it('rejects invalid phones', () => {
    expect(normalizeTrPhone('123')).toBeNull();
    expect(normalizeTrPhone('abcdef')).toBeNull();
    expect(normalizeTrPhone('055512345')).toBeNull();
    expect(normalizeTrPhone('4551234567')).toBeNull();
    expect(normalizeTrPhone('55512345678')).toBeNull();
    expect(normalizeTrPhone('+902121234567')).toBeNull();
  });

  it('treats blank as valid optional', () => {
    expect(isValidTrPhone('')).toBeTrue();
    expect(isValidTrPhone('   ')).toBeTrue();
    expect(isValidTrPhone(null)).toBeTrue();
    expect(isValidTrPhone(undefined)).toBeTrue();
    expect(isValidTrPhone('123')).toBeFalse();
    expect(isValidTrPhone('5551234567')).toBeTrue();
  });

  it('filters letters and caps digit count while typing', () => {
    expect(filterTrPhoneInput('484894664s8dfs8fsfwefw7few')).toBe('484894664887');
    expect(filterTrPhoneInput('+90abc5551234567xyz')).toBe('+905551234567');
    expect(filterTrPhoneInput('0555 123 45 67')).toBe('0555 123 45 67');
    expect(filterTrPhoneInput('555123456789999')).toBe('555123456789');
    expect(filterTrPhoneInput('5551234567890extra')).toBe('555123456789');
  });
});

describe('trPhoneValidator', () => {
  const validate = trPhoneValidator();

  it('allows empty and rejects garbage', () => {
    expect(validate(new FormControl(''))).toBeNull();
    expect(validate(new FormControl('  '))).toBeNull();
    expect(validate(new FormControl('5551234567'))).toBeNull();
    expect(validate(new FormControl('05551234567'))).toBeNull();
    expect(validate(new FormControl('+905551234567'))).toBeNull();
    expect(validate(new FormControl('123'))).toEqual({ trPhone: true });
    expect(validate(new FormControl('abcdef'))).toEqual({ trPhone: true });
  });
});
