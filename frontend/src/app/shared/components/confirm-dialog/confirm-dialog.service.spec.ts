import { TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmDialogService } from './confirm-dialog.service';

describe('ConfirmDialogService', () => {
  let service: ConfirmDialogService;
  let openSpy: jasmine.Spy;

  beforeEach(() => {
    openSpy = jasmine.createSpy('open');
    TestBed.configureTestingModule({
      providers: [
        ConfirmDialogService,
        { provide: NgbModal, useValue: { open: openSpy } },
      ],
    });
    service = TestBed.inject(ConfirmDialogService);
  });

  it('returns true when modal closes', (done) => {
    const componentInstance = {
      title: '',
      message: '',
      confirmLabel: '',
      cancelLabel: '',
      confirmButtonClass: '',
    };
    openSpy.and.returnValue({
      componentInstance,
      result: Promise.resolve(true),
    } as NgbModalRef);

    service
      .confirm({ title: 'T', message: 'M', confirmLabel: 'Sil' })
      .subscribe((ok) => {
        expect(ok).toBeTrue();
        expect(componentInstance.title).toBe('T');
        expect(componentInstance.confirmLabel).toBe('Sil');
        done();
      });
  });

  it('returns false when modal is dismissed', (done) => {
    openSpy.and.returnValue({
      componentInstance: {},
      result: Promise.reject('dismiss'),
    } as NgbModalRef);

    service.confirm({ title: 'T', message: 'M' }).subscribe((ok) => {
      expect(ok).toBeFalse();
      done();
    });
  });
});
