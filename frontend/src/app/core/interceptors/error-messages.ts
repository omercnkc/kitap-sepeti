/** Backend `code` → kullanıcıya gösterilen Türkçe mesaj (ui.md §9.4). */
export const ERROR_CODE_MESSAGES: Readonly<Record<string, string>> = {
  VALIDATION_FAILED: 'Girdiğiniz bilgileri kontrol edin.',
  MALFORMED_REQUEST: 'İstek geçersiz.',
  UNAUTHORIZED: 'Oturum açmanız gerekiyor.',
  FORBIDDEN: 'Bu işlem için yetkiniz yok.',
  NOT_FOUND: 'İstenen kaynak bulunamadı.',
  RESOURCE_NOT_FOUND: 'İstenen kaynak bulunamadı.',
  CONFLICT: 'İşlem çakışma nedeniyle tamamlanamadı.',
  SLUG_ALREADY_EXISTS: 'Bu kısa ad zaten kullanılıyor.',
  ISBN_ALREADY_EXISTS: 'Bu ISBN zaten kayıtlı.',
  RESOURCE_IN_USE: 'Kaynak kullanımda olduğu için işlem yapılamadı.',
  CONCURRENT_MODIFICATION: 'Kayıt başka biri tarafından değiştirildi; yenileyip tekrar deneyin.',
  CATEGORY_CYCLE: 'Kategori hiyerarşisinde döngü oluşamaz.',
  BOOK_NOT_PUBLISHABLE: 'Kitap yayınlanabilir durumda değil.',
  STOCK_BELOW_RESERVED: 'Stok rezervasyonun altına düşürülemez.',
  INSUFFICIENT_STOCK: 'Yeterli stok yok.',
  BOOK_NOT_AVAILABLE: 'Kitap satışa uygun değil.',
  AUTHENTICATION_UNAVAILABLE: 'Kimlik doğrulama şu an kullanılamıyor; biraz sonra tekrar deneyin.',
  INTERNAL_ERROR: 'Beklenmeyen bir hata oluştu.',
};

export function messageForErrorCode(code: string | undefined): string | undefined {
  if (!code) {
    return undefined;
  }
  return ERROR_CODE_MESSAGES[code];
}
