import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject } from 'rxjs';
import { SharedModule } from '../../../shared/shared.module';
import { BookDetailPageComponent } from './book-detail-page.component';

describe('BookDetailPageComponent', () => {
  let fixture: ComponentFixture<BookDetailPageComponent>;
  let httpMock: HttpTestingController;
  let paramMap$: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(async () => {
    paramMap$ = new BehaviorSubject(convertToParamMap({ id: 'b1' }));
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule, SharedModule],
      declarations: [BookDetailPageComponent],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { paramMap: paramMap$.asObservable() },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookDetailPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows book detail when found', () => {
    httpMock.expectOne('/api/books/b1').flush({
      id: 'b1',
      title: 'Detay Kitap',
      authors: [{ id: 'a1', name: 'Ali', slug: 'ali' }],
      publisher: { id: 'p1', name: 'Yayınevi', slug: 'yayinevi' },
      categories: [{ id: 'c1', name: 'Roman', slug: 'roman' }],
      priceAmount: 80,
      currency: 'TRY',
      inStock: true,
      description: 'Açıklama metni',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Detay Kitap');
    expect(el.textContent).toContain('Ali');
    expect(el.textContent).toContain('Açıklama metni');
  });

  it('shows not found on 404', () => {
    httpMock.expectOne('/api/books/b1').flush(
      { title: 'Not Found', status: 404, code: 'RESOURCE_NOT_FOUND' },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Kitap bulunamadı');
  });
});
