import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { LoginComponent } from './components/login/login.component';
import { SignupComponent } from './components/signup/signup.component';
import { NavbarComponent } from './components/navbar/navbar.component';
import { HomeComponent } from './components/home/home.component';
import { ErrorComponent } from './components/error/error.component';
import { AdmindashboardComponent } from './components/admindashboard/admindashboard.component';
import { AdminaddplanComponent } from './components/adminaddplan/adminaddplan.component';
import { AdminviewplansComponent } from './components/adminviewplans/adminviewplans.component';
import { AdminaddmemberComponent } from './components/adminaddmember/adminaddmember.component';
import { AdminviewmembersComponent } from './components/adminviewmembers/adminviewmembers.component';
import { AdminaddtrainerComponent } from './components/adminaddtrainer/adminaddtrainer.component';
import { AdminviewtrainersComponent } from './components/adminviewtrainers/adminviewtrainers.component';
import { AdminviewpaymentsComponent } from './components/adminviewpayments/adminviewpayments.component';
import { AdminviewattendanceComponent } from './components/adminviewattendance/adminviewattendance.component';
import { MemberprofileComponent } from './components/memberprofile/memberprofile.component';
import { MemberviewplanComponent } from './components/memberviewplan/memberviewplan.component';
import { MemberattendanceComponent } from './components/memberattendance/memberattendance.component';
import { MemberpaymentsComponent } from './components/memberpayments/memberpayments.component';
import { TrainerviewmembersComponent } from './components/trainerviewmembers/trainerviewmembers.component';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { OnboardingstepperComponent } from './components/onboardingstepper/onboardingstepper.component';
import { OnboardingplansComponent } from './components/onboardingplans/onboardingplans.component';
import { OnboardingtrainerComponent } from './components/onboardingtrainer/onboardingtrainer.component';
import { OnboardingprofileComponent } from './components/onboardingprofile/onboardingprofile.component';
import { OnboardingpaymentComponent } from './components/onboardingpayment/onboardingpayment.component';
import { OnboardingsuccessComponent } from './components/onboardingsuccess/onboardingsuccess.component';
import { MembertrainerComponent } from './components/membertrainer/membertrainer.component';
import { FaceEnrollComponent } from './components/faceenroll/faceenroll.component';
import { FaceScannerComponent } from './components/facescanner/facescanner.component';
import { FingerprintenrollComponent } from './components/fingerprintenroll/fingerprintenroll.component';
import { MemberfingerprintComponent } from './components/memberfingerprint/memberfingerprint.component';
import { FingerprintcheckinComponent } from './components/fingerprintcheckin/fingerprintcheckin.component';
import { LinechartComponent } from './components/linechart/linechart.component';
import { BarchartComponent } from './components/barchart/barchart.component';
import { DonutchartComponent } from './components/donutchart/donutchart.component';
import { ProgressbarsComponent } from './components/progressbars/progressbars.component';
import { HeatmapstripComponent } from './components/heatmapstrip/heatmapstrip.component';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    SignupComponent,
    NavbarComponent,
    HomeComponent,
    ErrorComponent,
    AdmindashboardComponent,
    AdminaddplanComponent,
    AdminviewplansComponent,
    AdminaddmemberComponent,
    AdminviewmembersComponent,
    AdminaddtrainerComponent,
    AdminviewtrainersComponent,
    AdminviewpaymentsComponent,
    AdminviewattendanceComponent,
    MemberprofileComponent,
    MemberviewplanComponent,
    MemberattendanceComponent,
    MemberpaymentsComponent,
    TrainerviewmembersComponent,
    OnboardingstepperComponent,
    OnboardingplansComponent,
    OnboardingtrainerComponent,
    OnboardingprofileComponent,
    OnboardingpaymentComponent,
    OnboardingsuccessComponent,
    MembertrainerComponent,
    FaceEnrollComponent,
    FaceScannerComponent,
    FingerprintenrollComponent,
    MemberfingerprintComponent,
    FingerprintcheckinComponent,
    LinechartComponent,
    BarchartComponent,
    DonutchartComponent,
    ProgressbarsComponent,
    HeatmapstripComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
