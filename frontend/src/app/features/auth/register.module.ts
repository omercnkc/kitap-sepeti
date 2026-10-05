import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { RegisterPageComponent } from './register-page/register-page.component';

const routes: Routes = [{ path: '', component: RegisterPageComponent }];

@NgModule({
  declarations: [RegisterPageComponent],
  // ReactiveFormsModule hazır — FormGroup’u sen bağlayacaksın
  imports: [SharedModule, ReactiveFormsModule, RouterModule.forChild(routes)],
})
export class RegisterModule {}
