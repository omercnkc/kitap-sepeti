import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { CatalogApi } from '../../../core/api/catalog.api';
import { BookDetail } from '../../../core/models';

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
  readonly bookId: string | null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly catalogApi: CatalogApi,
  ) {
    this.bookId = this.route.snapshot.paramMap.get('id');
  }

  ngOnInit(): void {
    if (!this.bookId) {
      this.stateSubject.next({ kind: 'notFound' });
      return;
    }

    this.catalogApi
      .getById(this.bookId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (book) => this.stateSubject.next({ kind: 'ready', book }),
        error: (err: { status?: number }) => {
          if (err && err.status === 404) {
            this.stateSubject.next({ kind: 'notFound' });
          } else {
            this.stateSubject.next({ kind: 'error' });
          }
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.stateSubject.complete();
  }
}
