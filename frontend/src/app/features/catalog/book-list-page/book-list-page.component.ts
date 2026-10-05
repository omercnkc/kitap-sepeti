import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { CatalogApi } from '../../../core/api/catalog.api';
import { BookSummary } from '../../../core/models';

type ListState =
  | { kind: 'loading' }
  | { kind: 'ready'; books: BookSummary[] }
  | { kind: 'empty' }
  | { kind: 'error' };

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

  constructor(private readonly catalogApi: CatalogApi) {}

  ngOnInit(): void {
    this.catalogApi
      .list({ page: 0, size: 10 })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page) => {
          if (!page.items || page.items.length === 0) {
            this.stateSubject.next({ kind: 'empty' });
          } else {
            this.stateSubject.next({ kind: 'ready', books: page.items });
          }
        },
        error: () => {
          this.stateSubject.next({ kind: 'error' });
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.stateSubject.complete();
  }
}
