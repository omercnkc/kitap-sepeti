import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { CartStore } from '../../../core/cart/cart.store';
import { ToastService } from '../../../core/services/toast.service';
import { SharedModule } from '../../../shared/shared.module';
import { BookFiltersComponent } from '../book-filters/book-filters.component';
import { bookFilterFromParams, BookListPageComponent } from './book-list-page.component';

describe('bookFilterFromParams', () => {
  it('maps sort/page and forces size=12', () => {
    const filter = bookFilterFromParams(
      convertToParamMap({
        sort: 'price_asc',
        page: '1',
        size: '10',
        categoryId: '',
      }),
    );
    expect(filter.sort).toBe('price_asc');
    expect(filter.page).toBe(1);
    expect(filter.size).toBe(12);
    expect(filter.categoryId).toBeNull();
  });
});

describe('BookListPageComponent', () => {
  let fixture: ComponentFixture<BookListPageComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
        RouterTestingModule.withRoutes([{ path: 'books', component: BookListPageComponent }]),
        SharedModule,
        NgbModule,
      ],
      declarations: [BookListPageComponent, BookFiltersComponent],
      providers: [
        {
          provide: AuthService,
          useValue: { isLoggedIn: () => false, isAdmin: () => false },
        },
        {
          provide: CartStore,
          useValue: { add: () => of({ itemCount: 1 }) },
        },
        {
          provide: ToastService,
          useValue: { success: jasmine.createSpy('success') },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookListPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushInitial(): void {
    httpMock.expectOne('/api/categories').flush([]);
    httpMock.expectOne((r) => r.url === '/api/books').flush({
      items: [
        {
          id: 'b1',
          title: 'Deneme',
          authors: [{ id: 'a1', name: 'Yazar', slug: 'yazar' }],
          publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
          priceAmount: 99.9,
          currency: 'TRY',
          inStock: true,
        },
      ],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();
  }

  it('loads books from CatalogApi with default page and size=12', () => {
    httpMock.expectOne('/api/categories').flush([]);
    const req = httpMock.expectOne((r) => r.url === '/api/books');
    expect(req.request.params.get('size')).toBe('12');
    expect(req.request.params.get('page')).toBe('0');
    req.flush({
      items: [
        {
          id: 'b1',
          title: 'Deneme',
          authors: [{ id: 'a1', name: 'Yazar', slug: 'yazar' }],
          publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
          priceAmount: 99.9,
          currency: 'TRY',
          inStock: true,
        },
      ],
      page: 0,
      size: 12,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Deneme');
  });

  it('writes categoryId and resets page on filter change', () => {
    flushInitial();
    fixture.componentInstance.onFilterChange({ categoryId: 'cat-1' });
    expect(router.navigate).toHaveBeenCalledWith(
      [],
      jasmine.objectContaining({
        queryParams: { page: 0, categoryId: 'cat-1' },
        queryParamsHandling: 'merge',
      }),
    );
  });

  it('blocks invalid price range without navigating', () => {
    flushInitial();
    (router.navigate as jasmine.Spy).calls.reset();
    fixture.componentInstance.onFilterChange({ minPrice: 100, maxPrice: 10 });
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
