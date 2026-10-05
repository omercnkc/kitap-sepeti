import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbPaginationModule } from '@ng-bootstrap/ng-bootstrap';
import { PaginationComponent } from './pagination.component';

describe('PaginationComponent', () => {
  let fixture: ComponentFixture<PaginationComponent>;
  let component: PaginationComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NgbPaginationModule],
      declarations: [PaginationComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(PaginationComponent);
    component = fixture.componentInstance;
    component.pageIndex = 0;
    component.pageSize = 20;
    component.totalElements = 45;
    component.totalPages = 3;
    fixture.detectChanges();
  });

  it('maps 0-based pageIndex to 1-based uiPage', () => {
    expect(component.uiPage).toBe(1);
    component.pageIndex = 2;
    expect(component.uiPage).toBe(3);
  });

  it('full layout is visible only when totalPages > 1', () => {
    component.layout = 'full';
    expect(component.visible).toBeTrue();
    component.totalPages = 1;
    expect(component.visible).toBeFalse();
  });

  it('simple layout shows 1/N and disables Prev on first page', () => {
    const simpleFixture = TestBed.createComponent(PaginationComponent);
    const simple = simpleFixture.componentInstance;
    simple.layout = 'simple';
    simple.pageIndex = 0;
    simple.pageSize = 12;
    simple.totalElements = 36;
    simple.totalPages = 3;
    simpleFixture.detectChanges();

    expect(simple.visible).toBeTrue();
    expect(simple.canGoPrev).toBeFalse();
    expect(simple.canGoNext).toBeTrue();
    const status = (simpleFixture.nativeElement as HTMLElement).querySelector(
      '.app-pagination__status',
    );
    expect(status?.textContent?.replace(/\s+/g, ' ').trim()).toBe('1 / 3');
  });

  it('simple layout disables Next on last page and emits goPrev', () => {
    const simpleFixture = TestBed.createComponent(PaginationComponent);
    const simple = simpleFixture.componentInstance;
    simple.layout = 'simple';
    simple.pageIndex = 2;
    simple.totalPages = 3;
    simple.pageSize = 12;
    simple.totalElements = 36;
    simpleFixture.detectChanges();

    expect(simple.canGoNext).toBeFalse();
    expect(simple.canGoPrev).toBeTrue();
    const spy = jasmine.createSpy('pageIndexChange');
    simple.pageIndexChange.subscribe(spy);
    simple.goPrev();
    expect(spy).toHaveBeenCalledWith(1);
  });

  it('emits 0-based pageIndexChange from Ngb pageChange', () => {
    const spy = jasmine.createSpy('pageIndexChange');
    component.pageIndexChange.subscribe(spy);
    component.onPageChange(2);
    expect(spy).toHaveBeenCalledWith(1);
  });

  it('does not emit when staying on same page', () => {
    const spy = jasmine.createSpy('pageIndexChange');
    component.pageIndexChange.subscribe(spy);
    component.onPageChange(1);
    expect(spy).not.toHaveBeenCalled();
  });

  it('ignores page beyond last', () => {
    const spy = jasmine.createSpy('pageIndexChange');
    component.pageIndexChange.subscribe(spy);
    component.onPageChange(4);
    expect(spy).not.toHaveBeenCalled();
  });
});
