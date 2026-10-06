import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { BehaviorSubject, EMPTY, Subject } from 'rxjs';
import { catchError, switchMap, takeUntil, tap } from 'rxjs/operators';
import { CatalogApi } from '../../../core/api/catalog.api';
import { AuthService } from '../../../core/auth/auth.service';
import { CartStore } from '../../../core/cart/cart.store';
import { toProblemDetail } from '../../../core/interceptors/error.interceptor';
import { BookDetail } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';

type DetailState =
  | { kind: 'loading' }
  | { kind: 'ready'; book: BookDetail }
  | { kind: 'notFound' }
  | { kind: 'error' };

@Component({
  selector: 'app-book-detail-page',
  templateUrl: './book-detail-page.component.html',
  styleUrls: ['./book-detail-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookDetailPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly stateSubject = new BehaviorSubject<DetailState>({ kind: 'loading' });

  readonly state$ = this.stateSubject.asObservable();
  adding = false;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly catalogApi: CatalogApi,
    private readonly auth: AuthService,
    private readonly cartStore: CartStore,
    private readonly toast: ToastService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        takeUntil(this.destroy$),
        tap(() => this.stateSubject.next({ kind: 'loading' })),
        switchMap((params) => {
          const id = params.get('id');
          if (!id) {
            this.stateSubject.next({ kind: 'notFound' });
            return EMPTY;
          }
          return this.catalogApi.getById(id).pipe(
            catchError((err: unknown) => {
              const problem = toProblemDetail(err);
              if (problem.status === 404) {
                this.stateSubject.next({ kind: 'notFound' });
              } else {
                this.stateSubject.next({ kind: 'error' });
              }
              return EMPTY;
            }),
          );
        }),
      )
      .subscribe((book) => this.stateSubject.next({ kind: 'ready', book }));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.stateSubject.complete();
  }

  authorsLabel(book: BookDetail): string {
    if (!book.authors || book.authors.length === 0) {
      return '';
    }
    return book.authors.map((a) => a.name).join(', ');
  }

  /** ADMIN Vitrin’de sepete eklemez (Özellik 6 ek). */
  get showAddToCart(): boolean {
    return !this.auth.isAdmin();
  }

  categoriesLabel(book: BookDetail): string {
    if (!book.categories || book.categories.length === 0) {
      return '';
    }
    return book.categories.map((c) => c.name).join(', ');
  }

  onAddToCart(book: BookDetail): void {
    if (!this.showAddToCart) {
      return;
    }
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], {
        queryParams: { returnUrl: this.router.url },
      });
      return;
    }
    if (this.adding || !book.inStock) {
      return;
    }
    this.adding = true;
    this.cdr.markForCheck();
    this.cartStore
      .add(book.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.adding = false;
          this.toast.success('Sepete eklendi');
          this.cdr.markForCheck();
        },
        error: () => {
          this.adding = false;
          this.cdr.markForCheck();
        },
      });
  }
}
