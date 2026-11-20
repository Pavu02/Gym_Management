import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './components/home/home.component';
import { LoginComponent } from './components/login/login.component';
import { SignupComponent } from './components/signup/signup.component';
import { ErrorComponent } from './components/error/error.component';
import { AdmindashboardComponent } from './components/admindashboard/admindashboard.component';
import { AdminviewplansComponent } from './components/adminviewplans/adminviewplans.component';
import { AdminaddplanComponent } from './components/adminaddplan/adminaddplan.component';
import { AdminviewmembersComponent } from './components/adminviewmembers/adminviewmembers.component';
import { AdminaddmemberComponent } from './components/adminaddmember/adminaddmember.component';
import { AdminviewtrainersComponent } from './components/adminviewtrainers/adminviewtrainers.component';
import { AdminaddtrainerComponent } from './components/adminaddtrainer/adminaddtrainer.component';
import { AdminviewpaymentsComponent } from './components/adminviewpayments/adminviewpayments.component';
import { AdminviewattendanceComponent } from './components/adminviewattendance/adminviewattendance.component';
import { MemberprofileComponent } from './components/memberprofile/memberprofile.component';
import { MemberviewplanComponent } from './components/memberviewplan/memberviewplan.component';
import { MemberattendanceComponent } from './components/memberattendance/memberattendance.component';
import { MemberpaymentsComponent } from './components/memberpayments/memberpayments.component';
import { TrainerviewmembersComponent } from './components/trainerviewmembers/trainerviewmembers.component';
import { authGuard } from './guards/auth.guard';

const routes: Routes = [
  { path: '', redirectTo: 'home', pathMatch: 'full' },
  { path: 'home', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent },

  // Admin routes
  { path: 'admin/dashboard', component: AdmindashboardComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/plans', component: AdminviewplansComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/plans/add', component: AdminaddplanComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/plans/edit/:id', component: AdminaddplanComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/members', component: AdminviewmembersComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/members/add', component: AdminaddmemberComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/members/edit/:id', component: AdminaddmemberComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/trainers', component: AdminviewtrainersComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/trainers/add', component: AdminaddtrainerComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/trainers/edit/:id', component: AdminaddtrainerComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/payments', component: AdminviewpaymentsComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },
  { path: 'admin/attendance', component: AdminviewattendanceComponent, canActivate: [authGuard], data: { role: 'ADMIN' } },

  // Member routes
  { path: 'member/profile', component: MemberprofileComponent, canActivate: [authGuard], data: { role: 'MEMBER' } },
  { path: 'member/plan', component: MemberviewplanComponent, canActivate: [authGuard], data: { role: 'MEMBER' } },
  { path: 'member/attendance', component: MemberattendanceComponent, canActivate: [authGuard], data: { role: 'MEMBER' } },
  { path: 'member/payments', component: MemberpaymentsComponent, canActivate: [authGuard], data: { role: 'MEMBER' } },

  // Trainer routes
  { path: 'trainer/members', component: TrainerviewmembersComponent, canActivate: [authGuard], data: { role: 'TRAINER' } },

  // Fallback
  { path: 'error', component: ErrorComponent },
  { path: '**', component: ErrorComponent }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
