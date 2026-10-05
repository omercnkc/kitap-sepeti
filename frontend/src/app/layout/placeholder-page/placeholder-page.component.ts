import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/** Lazy feature modülleri gelene kadar geçici içerik. */
@Component({
  selector: 'app-placeholder-page',
  templateUrl: './placeholder-page.component.html',
  styleUrls: ['./placeholder-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaceholderPageComponent implements OnInit {
  title = 'Sayfa';

  constructor(private readonly route: ActivatedRoute) {}

  ngOnInit(): void {
    const dataTitle = this.route.snapshot.data['title'];
    if (typeof dataTitle === 'string' && dataTitle.length > 0) {
      this.title = dataTitle;
    }
  }
}
