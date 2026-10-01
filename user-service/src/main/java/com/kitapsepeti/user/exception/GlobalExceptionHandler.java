package com.kitapsepeti.user.exception;

import com.kitapsepeti.common.error.ProblemDetailExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller katmanından çıkan her hatayı RFC 9457 ProblemDetail'e çevirir; tüm handler'lar ortak tabandan
 * gelir. DB kısıt ihlali CONFLICT olur, logda yalnızca constraint adı yer alır (e-posta gibi değerler değil).
 * Security filtrelerinde oluşan 401/403 buraya ulaşmaz; onları common'daki security handler'ları yazar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailExceptionHandler {

}
