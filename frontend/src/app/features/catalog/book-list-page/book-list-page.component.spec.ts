import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { SharedModule } from '../../../shared/shared.module';
import { bookFilterFromParams, BookListPageComponent } from './book-list-page.component';

describe('bookFilterFromParams', () => {
  it('maps sort/page and omits blanks', () => {
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
    expect(filter.size).toBe(10);
    expect(filter.categoryId).toBeNull();
  });
});

describe('BookListPageComponent', () => {
  let fixture: ComponentFixture<BookListPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
        RouterTestingModule.withRoutes([{ path: 'books', component: BookListPageComponent }]),
        SharedModule,
      ],
      declarations: [BookListPageComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(BookListPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('loads books from CatalogApi with default page/size', () => {
    const req = httpMock.expectOne((r) => r.url === '/api/books');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
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
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Deneme');
  });
});
