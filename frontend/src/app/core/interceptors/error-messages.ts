/** Backend `code` → kullanıcıya gösterilen Türkçe mesaj (ui.md §9.4). */
export const ERROR_CODE_MESSAGES: Readonly<Record<string, string>> = {
  VALIDATION_FAILED: 'Girdiğiniz bilgileri kontrol edin.',
  MALFORMED_REQUEST: 'İstek geçersiz.',
  UNAUTHORIZED: 'Oturum açmanız gerekiyor.',
  INVALID_CREDENTIALS: 'E-posta veya parola hatalı.',
  INVALID_REFRESH_TOKEN: 'Oturum süresi doldu; tekrar giriş yapın.',
  EMAIL_ALREADY_EXISTS: 'Bu e-posta zaten kayıtlı.',
  ACCOUNT_SUSPENDED: 'Hesabınız askıya alınmış.',
  FORBIDDEN: 'Bu işlem için yetkiniz yok.',
  NOT_FOUND: 'İstenen kaynak bulunamadı.',
  RESOURCE_NOT_FOUND: 'İstenen kaynak bulunamadı.',
  CONFLICT: 'İşlem çakışma nedeniyle tamamlanamadı.',
  SLUG_ALREADY_EXISTS: 'Bu kısa ad zaten kullanılıyor.',
  ISBN_ALREADY_EXISTS: 'Bu ISBN zaten kayıtlı.',
  RESOURCE_IN_USE: 'Bu kayıt kullanımda olduğu için silinemez.',
  CONCURRENT_MODIFICATION: 'Kayıt başka biri tarafından değiştirildi; yenileyip tekrar deneyin.',
  CATEGORY_CYCLE: 'Bir kategoriyi kendi altına veya alt kategorisine taşıyamazsınız.',
  BOOK_NOT_PUBLISHABLE: 'Kitap yayınlanabilir durumda değil.',
  STOCK_BELOW_RESERVED: 'Stok rezervasyonun altına düşürülemez.',
  INSUFFICIENT_STOCK: 'Yeterli stok yok.',
  BOOK_NOT_AVAILABLE: 'Kitap satışa uygun değil.',
  CART_LINE_LIMIT_EXCEEDED: 'Sepetteki farklı kitap sayısı sınırına ulaşıldı.',
  CART_QUANTITY_LIMIT_EXCEEDED: 'Bu kitap için sepet adet sınırına ulaşıldı.',
  DEFAULT_ADDRESS_REQUIRED: 'Önce başka bir adresi varsayılan yapın.',
  ORDER_PENDING_EXISTS: 'Bekleyen bir siparişiniz var; önce onu tamamlayın veya bekleyin.',
  CART_UNAVAILABLE: 'Sepet şu an kullanılamıyor; biraz sonra tekrar deneyin.',
  PAYMENT_UNAVAILABLE: 'Ödeme şu an kullanılamıyor; biraz sonra tekrar deneyin.',
  ORDER_UNAVAILABLE: 'Sipariş servisi şu an kullanılamıyor; biraz sonra tekrar deneyin.',
  CHECKOUT_INTERRUPTED: 'Ödeme kesintiye uğradı; sipariş durumunu kontrol edin.',
  CATALOG_UNAVAILABLE: 'Katalog şu an kullanılamıyor; biraz sonra tekrar deneyin.',
  AUTHENTICATION_UNAVAILABLE: 'Kimlik doğrulama şu an kullanılamıyor; biraz sonra tekrar deneyin.',
  INTERNAL_ERROR: 'Beklenmeyen bir hata oluştu.',
};

export function messageForErrorCode(code: string | undefined): string | undefined {
  if (!code) {
    return undefined;
  }
  return ERROR_CODE_MESSAGES[code];
}
