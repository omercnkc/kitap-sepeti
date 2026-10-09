import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, TemplateRef } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { NgbOffcanvas } from '@ng-bootstrap/ng-bootstrap';
import { BehaviorSubject, EMPTY, Observable, Subject, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, takeUntil, tap } from 'rxjs/operators';
import { CatalogApi } from '../../../core/api/catalog.api';
import { AuthService } from '../../../core/auth/auth.service';
import { CartStore } from '../../../core/cart/cart.store';
import { BookFilter, BookSummary, CategoryTree, PageResponse } from '../../../core/models';
import { ToastService } from '../../../core/services/toast.service';
import { BookFilterChange } from '../book-filters/book-filters.component';

type ListState =
  | { kind: 'loading' }
  | { kind: 'ready'; page: PageResponse<BookSummary> }
  | { kind: 'empty'; page: PageResponse<BookSummary> }
  | { kind: 'error' };

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 12;
const SEARCH_MIN_LEN = 2;

const CLEARABLE_PARAMS = [
  'q',
  'categoryId',
  'authorId',
  'minPrice',
  'maxPrice',
  'sort',
  'page',
] as const;

@Component({
  selector: 'app-book-list-page',
  templateUrl: './book-list-page.component.html',
  styleUrls: ['./book-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookListPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly stateSubject = new BehaviorSubject<ListState>({ kind: 'loading' });
  private readonly categoriesSubject = new BehaviorSubject<CategoryTree[]>([]);
  private readonly priceErrorSubject = new BehaviorSubject<string | null>(null);

  readonly state$ = this.stateSubject.asObservable();
  readonly categories$ = this.categoriesSubject.asObservable();
  readonly priceRangeError$ = this.priceErrorSubject.asObservable();
  readonly filter$: Observable<BookFilter> = this.route.queryParamMap.pipe(
    map((params) => bookFilterFromParams(params)),
  );
  readonly searchControl = new FormControl('');

  constructor(
    private readonly catalogApi: CatalogApi,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly offcanvas: NgbOffcanvas,
    private readonly auth: AuthService,
    private readonly cartStore: CartStore,
    private readonly toast: ToastService,
  ) {}

  ngOnInit(): void {
    this.catalogApi
      .categories()
      .pipe(
        takeUntil(this.destroy$),
        catchError(() => of([] as CategoryTree[])),
      )
      .subscribe((cats) => this.categoriesSubject.next(cats));

    this.route.queryParamMap
      .pipe(
        takeUntil(this.destroy$),
        tap((params) => {
          const committed = optionalSearchQuery(params.get('q')) ?? '';
          const current = (this.searchControl.value ?? '').trim();
          const controlCommitted = current.length >= SEARCH_MIN_LEN ? current : '';
          // Kısa taslak yazarken input'u silme; yalnızca URL'deki kayıtlı q ile hizala
          if (committed !== controlCommitted) {
            this.searchControl.setValue(committed, { emitEvent: false });
          }
          this.priceErrorSubject.next(null);
          this.stateSubject.next({ kind: 'loading' });
        }),
        switchMap((params) => {
          const filter = bookFilterFromParams(params);
          return this.catalogApi.list(filter).pipe(
            catchError(() => {
              this.stateSubject.next({ kind: 'error' });
              return EMPTY;
            }),
          );
        }),
      )
      .subscribe((page) => {
        if (!page.items || page.items.length === 0) {
          this.stateSubject.next({ kind: 'empty', page });
        } else {
          this.stateSubject.next({ kind: 'ready', page });
        }
      });

    this.searchControl.valueChanges
      .pipe(
        debounceTime(300),
        map((raw) => (raw ?? '').trim()),
        distinctUntilChanged(),
        takeUntil(this.destroy$),
      )
      .subscribe((trimmed) => {
        const q = trimmed.length >= SEARCH_MIN_LEN ? trimmed : null;
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { q, page: 0 },
          queryParamsHandling: 'merge',
        });
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.stateSubject.complete();
    this.categoriesSubject.complete();
    this.priceErrorSubject.complete();
  }

  openFilters(content: TemplateRef<unknown>): void {
    this.offcanvas.open(content, {
      position: 'start',
      panelClass: 'book-filters-offcanvas',
    });
  }

  onFilterChange(change: BookFilterChange): void {
    if (
      change.minPrice !== undefined &&
      change.maxPrice !== undefined &&
      change.minPrice !== null &&
      change.maxPrice !== null &&
      change.minPrice > change.maxPrice
    ) {
      this.priceErrorSubject.next('Minimum fiyat, maksimum fiyattan büyük olamaz.');
      return;
    }

    this.priceErrorSubject.next(null);
    const queryParams: Record<string, string | number | null> = { page: 0 };

    if (change.categoryId !== undefined) {
      queryParams['categoryId'] = change.categoryId;
    }
    if (change.sort !== undefined) {
      queryParams['sort'] = change.sort;
    }
    if (change.minPrice !== undefined) {
      queryParams['minPrice'] = change.minPrice;
    }
    if (change.maxPrice !== undefined) {
      queryParams['maxPrice'] = change.maxPrice;
    }

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });
  }

  onClearFilters(): void {
    this.priceErrorSubject.next(null);
    const queryParams: Record<string, null> = {};
    CLEARABLE_PARAMS.forEach((key) => {
      queryParams[key] = null;
    });
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
    });
  }

  onPageIndexChange(pageIndex: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: pageIndex },
      queryParamsHandling: 'merge',
    });
  }

  onAddToCart(book: BookSummary): void {
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], {
        queryParams: { returnUrl: this.router.url },
      });
      return;
    }
    this.cartStore
      .add(book.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.toast.success('Sepete eklendi'),
      });
  }
}

export function bookFilterFromParams(params: ParamMap): BookFilter {
  return {
    q: optionalSearchQuery(params.get('q')),
    categoryId: optionalString(params.get('categoryId')),
    authorId: optionalString(params.get('authorId')),
    minPrice: optionalNumber(params.get('minPrice')),
    maxPrice: optionalNumber(params.get('maxPrice')),
    sort: optionalString(params.get('sort')),
    page: optionalInt(params.get('page'), DEFAULT_PAGE),
    // Vitrin: sayfa başına 12 (3×4 lg); URL size yok sayılır
    size: DEFAULT_SIZE,
  };
}

function optionalSearchQuery(value: string | null): string | null {
  if (value === null) {
    return null;
  }
  const trimmed = value.trim();
  return trimmed.length >= SEARCH_MIN_LEN ? trimmed : null;
}

function optionalString(value: string | null): string | null {
  if (value === null || value.trim() === '') {
    return null;
  }
  return value;
}

function optionalNumber(value: string | null): number | null {
  if (value === null || value.trim() === '') {
    return null;
  }
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

function optionalInt(value: string | null, fallback: number): number {
  if (value === null || value.trim() === '') {
    return fallback;
  }
  const n = Number.parseInt(value, 10);
  return Number.isFinite(n) && n >= 0 ? n : fallback;
}
