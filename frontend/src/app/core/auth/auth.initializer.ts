import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';

/**
 * Açılışta refresh token varsa oturumu sessizce restore eder.
 * Başarısız/yoksa uygulamayı bloklamaz.
 */
export function authInitializer(
  authService: AuthService,
  tokenStorage: TokenStorageService,
): () => Promise<void> {
  return () => {
    if (!tokenStorage.getRefreshToken()) {
      return Promise.resolve();
    }

    return firstValueFrom(
      authService.refresh().pipe(
        switchMap(() => authService.loadCurrentUser()),
        map(() => undefined),
        catchError(() => {
          authService.logout();
          return of(undefined);
        }),
      ),
    );
  };
}
