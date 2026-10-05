import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { ShellComponent } from './shell/shell.component';
import { HeaderComponent } from './header/header.component';
import { FooterComponent } from './footer/footer.component';
import { NotFoundComponent } from './not-found/not-found.component';

@NgModule({
  declarations: [ShellComponent, HeaderComponent, FooterComponent, NotFoundComponent],
  imports: [SharedModule],
  exports: [ShellComponent, NotFoundComponent],
})
export class LayoutModule {}
