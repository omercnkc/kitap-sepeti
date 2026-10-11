import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { CatalogApi } from '../../../core/api/catalog.api';
import { AuthService } from '../../../core/auth/auth.service';
import { BookSummary } from '../../../core/models';

const CAROUSEL_SIZE = 12;
const AUTO_MS = 3500;

@Component({
  selector: 'app-book-carousel',
  templateUrl: './book-carousel.component.html',
  styleUrls: ['./book-carousel.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookCarouselComponent implements OnInit, OnDestroy {
  private readonly destroy$ = new Subject<void>();
  private timer: ReturnType<typeof setInterval> | null = null;
  private paused = false;

  @ViewChild('track', { static: false }) track?: ElementRef<HTMLElement>;

  @Output() readonly addToCart = new EventEmitter<BookSummary>();

  books: BookSummary[] = [];
  activeIndex = 0;
  loading = true;
  failed = false;

  constructor(
    private readonly catalogApi: CatalogApi,
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.catalogApi
      .list({ sort: 'newest', page: 0, size: CAROUSEL_SIZE })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (page) => {
          this.books = page.items ?? [];
          this.loading = false;
          this.failed = false;
          this.cdr.markForCheck();
          if (this.books.length > 1 && !this.prefersReducedMotion()) {
            this.startAuto();
          }
        },
        error: () => {
          this.books = [];
          this.loading = false;
          this.failed = true;
          this.cdr.markForCheck();
        },
      });
  }

  ngOnDestroy(): void {
    this.stopAuto();
    this.destroy$.next();
    this.destroy$.complete();
  }

  get visible(): boolean {
    return !this.loading && !this.failed && this.books.length > 0;
  }

  trackById(_index: number, book: BookSummary): string {
    return book.id;
  }

  authorsLabel(book: BookSummary): string {
    if (!book.authors || book.authors.length === 0) {
      return '';
    }
    return book.authors.map((a) => a.name).join(', ');
  }

  openBook(book: BookSummary): void {
    void this.router.navigate(['/books', book.id]);
  }

  onAddToCart(event: Event, book: BookSummary): void {
    event.preventDefault();
    event.stopPropagation();
    if (!book.inStock || this.auth.isAdmin()) {
      return;
    }
    this.addToCart.emit(book);
  }

  prev(): void {
    if (this.books.length === 0) {
      return;
    }
    this.activeIndex = (this.activeIndex - 1 + this.books.length) % this.books.length;
    this.scrollToActive();
    this.cdr.markForCheck();
  }

  next(): void {
    if (this.books.length === 0) {
      return;
    }
    this.activeIndex = (this.activeIndex + 1) % this.books.length;
    this.scrollToActive();
    this.cdr.markForCheck();
  }

  goTo(index: number): void {
    if (index < 0 || index >= this.books.length) {
      return;
    }
    this.activeIndex = index;
    this.scrollToActive();
    this.cdr.markForCheck();
  }

  onPointerEnter(): void {
    this.paused = true;
  }

  onPointerLeave(): void {
    this.paused = false;
  }

  private startAuto(): void {
    this.stopAuto();
    this.timer = setInterval(() => {
      if (!this.paused) {
        this.next();
      }
    }, AUTO_MS);
  }

  private stopAuto(): void {
    if (this.timer != null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  private scrollToActive(): void {
    const track = this.track?.nativeElement;
    if (!track) {
      return;
    }
    const card = track.children.item(this.activeIndex) as HTMLElement | null;
    if (!card) {
      return;
    }
    const left = card.offsetLeft - (track.clientWidth - card.offsetWidth) / 2;
    track.scrollTo({ left: Math.max(0, left), behavior: 'smooth' });
  }

  private prefersReducedMotion(): boolean {
    if (typeof window === 'undefined' || !window.matchMedia) {
      return false;
    }
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
}
