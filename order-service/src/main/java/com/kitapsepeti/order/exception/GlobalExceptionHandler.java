package com.kitapsepeti.order.exception;

import com.kitapsepeti.common.error.ProblemDetailExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Controller katmanından çıkan her hatayı RFC 9457 ProblemDetail'e çevirir; ortak handler'lar tabandan gelir.
 * Siparişe özgü kodlar ve DB kısıtı → kod eşlemesi uçlarla birlikte (Adım 4) eklenecek. Yol, servisin
 * {@code RequestPathMasker} bean'iyle maskelenir ({@code SecurityConfig}).
 * Security filtrelerinde oluşan 401/403/503 buraya ulaşmaz; onları common'daki security handler'ları yazar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailExceptionHandler {

}
