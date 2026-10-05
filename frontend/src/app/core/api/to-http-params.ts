import { HttpParams } from '@angular/common/http';

/**
 * null / undefined / '' değerleri atar; kalanları string query param yapar.
 */
export function toHttpParams(
  obj: Record<string, string | number | boolean | null | undefined> | object,
): HttpParams {
  let params = new HttpParams();
  Object.keys(obj).forEach((key) => {
    const value = (obj as Record<string, unknown>)[key];
    if (value === null || value === undefined || value === '') {
      return;
    }
    params = params.set(key, String(value));
  });
  return params;
}
