import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { BehaviorSubject, EMPTY, Subject } from 'rxjs';
import { catchError, switchMap, takeUntil, tap } from 'rxjs/operators';
import { CatalogApi } from '../../../core/api/catalog.api';
import { BookFilter, BookSummary, PageResponse } from '../../../core/models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'ready'; page: PageResponse<BookSummary> }
  | { kind: 'empty'; page: PageResponse<BookSummary> }
  | { kind: 'error' };

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;

@Component({
  selector: 'app-book-list-page',
  templateUrl: './book-list-page.component.html',
  styleUrls: ['./book-list-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookListPageComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private readonly stateSubject = new BehaviorSubject<ListState>({ kind: 'loading' });

  readonly state$ = this.stateSubject.asObservable();

  constructor(
    private readonly catalogApi: CatalogApi,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(
        takeUntil(this.destroy$),
        tap(() => this.stateSubject.next({ kind: 'loading' })),
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
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.stateSubject.complete();
  }

  onPageIndexChange(pageIndex: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: pageIndex },
      queryParamsHandling: 'merge',
    });
  }

  onAddToCart(_book: BookSummary): void {
    // CartStore UI-5
  }
}

export function bookFilterFromParams(params: ParamMap): BookFilter {
  return {
    categoryId: optionalString(params.get('categoryId')),
    publisherId: optionalString(params.get('publisherId')),
    authorId: optionalString(params.get('authorId')),
    minPrice: optionalNumber(params.get('minPrice')),
    maxPrice: optionalNumber(params.get('maxPrice')),
    sort: optionalString(params.get('sort')),
    page: optionalInt(params.get('page'), DEFAULT_PAGE),
    size: optionalInt(params.get('size'), DEFAULT_SIZE),
  };
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
