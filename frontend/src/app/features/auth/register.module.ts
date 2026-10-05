import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { RegisterPageComponent } from './register-page/register-page.component';

const routes: Routes = [{ path: '', component: RegisterPageComponent }];

@NgModule({
  declarations: [RegisterPageComponent],
  imports: [SharedModule, RouterModule.forChild(routes)],
})
export class RegisterModule {}
