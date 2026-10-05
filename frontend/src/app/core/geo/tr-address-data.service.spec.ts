import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TrAddressDataService } from './tr-address-data.service';
import { TrIl, TrIlce, TrMahalle } from './tr-address.models';

describe('TrAddressDataService', () => {
  let service: TrAddressDataService;
  let httpMock: HttpTestingController;

  const iller: TrIl[] = [
    { sehir_id: '34', sehir_adi: 'İSTANBUL' },
    { sehir_id: '6', sehir_adi: 'ANKARA' },
  ];

  const ilceler: TrIlce[] = [
    { ilce_id: '1103', ilce_adi: 'KADIKÖY', sehir_id: '34', sehir_adi: 'İSTANBUL' },
    { ilce_id: '2000', ilce_adi: 'ÇANKAYA', sehir_id: '6', sehir_adi: 'ANKARA' },
  ];

  const index = { '1103': [2], '2000': [1] };

  const mahalleler2: TrMahalle[] = [
    {
      mahalle_id: '1',
      mahalle_adi: 'CAFERAĞA MAHALLESİ',
      ilce_id: '1103',
      ilce_adi: 'KADIKÖY',
      sehir_id: '34',
      sehir_adi: 'İSTANBUL',
    },
    {
      mahalle_id: '9',
      mahalle_adi: 'DİĞER',
      ilce_id: '9999',
      ilce_adi: 'X',
      sehir_id: '34',
      sehir_adi: 'İSTANBUL',
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(TrAddressDataService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getIller: first call GETs, second call uses cache', () => {
    let first: TrIl[] | undefined;
    let second: TrIl[] | undefined;

    service.getIller().subscribe((v) => (first = v));
    const req = httpMock.expectOne('assets/geo/iller.json');
    expect(req.request.method).toBe('GET');
    req.flush(iller);
    expect(first).toEqual(iller);

    service.getIller().subscribe((v) => (second = v));
    httpMock.expectNone('assets/geo/iller.json');
    expect(second).toEqual(iller);
  });

  it('getIlceler: loads ilceler once and filters by sehir_id', () => {
    let result: TrIlce[] | undefined;
    service.getIlceler('34').subscribe((v) => (result = v));
    httpMock.expectOne('assets/geo/ilceler.json').flush(ilceler);
    expect(result).toEqual([ilceler[0]]);

    let again: TrIlce[] | undefined;
    service.getIlceler('6').subscribe((v) => (again = v));
    httpMock.expectNone('assets/geo/ilceler.json');
    expect(again).toEqual([ilceler[1]]);
  });

  it('getMahalleler: lazy-loads only indexed part and filters by ilce_id', () => {
    let result: TrMahalle[] | undefined;
    service.getMahalleler('1103').subscribe((v) => (result = v));

    httpMock.expectOne('assets/geo/mahalle-index.json').flush(index);
    httpMock.expectOne('assets/geo/mahalleler-2.json').flush(mahalleler2);
    httpMock.expectNone('assets/geo/mahalleler-1.json');
    expect(result).toEqual([mahalleler2[0]]);

    let cached: TrMahalle[] | undefined;
    service.getMahalleler('1103').subscribe((v) => (cached = v));
    httpMock.expectNone('assets/geo/mahalle-index.json');
    httpMock.expectNone('assets/geo/mahalleler-2.json');
    expect(cached).toEqual([mahalleler2[0]]);
  });
});
