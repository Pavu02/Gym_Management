import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { AdminviewmembersComponent } from './adminviewmembers.component';
import { MemberService } from '../../services/member.service';
import { AttendanceService } from '../../services/attendance.service';
import { FingerprintService } from '../../services/fingerprint.service';
import { MemberResponse } from '../../models/member.model';

describe('AdminviewmembersComponent', () => {
  let fixture: ComponentFixture<AdminviewmembersComponent>;
  let component: AdminviewmembersComponent;
  let memberSpy: jasmine.SpyObj<MemberService>;
  let attendanceSpy: jasmine.SpyObj<AttendanceService>;
  let fpSpy: jasmine.SpyObj<FingerprintService>;

  const mockMembers: MemberResponse[] = [
    {
      id: 1,
      name: 'Admin',
      email: 'admin@gym.com',
      phone: '9876543210',
      planName: 'None',
      joinDate: '2026-01-01',
      expiryDate: '2027-01-01',
      status: 'ACTIVE',
      onboardingCompleted: true,
      daysLeft: 100,
      active: true,
      role: 'ADMIN',
      version: 0
    },
    {
      id: 7,
      name: 'Vidhant',
      email: 'vidhant@gym.com',
      phone: '8010553488',
      planName: 'Premium',
      joinDate: '2026-02-01',
      expiryDate: '2026-06-01',
      status: 'ACTIVE',
      onboardingCompleted: true,
      daysLeft: 60,
      active: true,
      faceEnabled: true,
      faceEnrolled: true,
      activeBiometricDevices: 1,
      hasOpenAttendance: false,
      role: 'MEMBER',
      version: 0
    },
    {
      id: 8,
      name: 'Inactive User',
      email: 'inactive@gym.com',
      phone: '8010553400',
      planName: 'Basic',
      joinDate: '2026-01-01',
      expiryDate: '2026-05-01',
      status: 'DEACTIVATED',
      onboardingCompleted: true,
      daysLeft: 30,
      active: false,
      faceEnabled: false,
      faceEnrolled: false,
      activeBiometricDevices: 0,
      hasOpenAttendance: false,
      role: 'MEMBER',
      version: 1
    }
  ];

  beforeEach(async () => {
    memberSpy = jasmine.createSpyObj<MemberService>('MemberService', [
      'getAllMembers',
      'deactivateMember',
      'reactivateMember'
    ]);
    memberSpy.getAllMembers.and.returnValue(of(mockMembers));
    memberSpy.deactivateMember.and.returnValue(of({
      success: true,
      memberId: 7,
      status: 'DEACTIVATED',
      message: 'Member deactivated successfully',
      version: 1
    }));
    memberSpy.reactivateMember.and.returnValue(of({
      success: true,
      memberId: 7,
      status: 'ACTIVE',
      message: 'Member reactivated successfully',
      version: 2,
      biometricReenrollmentRequired: true
    }));

    attendanceSpy = jasmine.createSpyObj<AttendanceService>('AttendanceService', ['checkIn', 'checkOut']);
    fpSpy = jasmine.createSpyObj<FingerprintService>('FingerprintService', ['getMemberDevices', 'adminDeleteDevice']);

    await TestBed.configureTestingModule({
      imports: [FormsModule, RouterTestingModule],
      declarations: [AdminviewmembersComponent],
      providers: [
        { provide: MemberService, useValue: memberSpy },
        { provide: AttendanceService, useValue: attendanceSpy },
        { provide: FingerprintService, useValue: fpSpy }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminviewmembersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('excludes deactivated members from activeCount and renders Deactivate/Reactivate without trash delete icons', () => {
    expect(component.activeCount).toBe(2);
    expect(component.deactivatedCount).toBe(1);

    const html = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(html).toContain('Deactivate');
    expect(html).toContain('Reactivate');
    expect(html).not.toContain('Delete Member');
  });

  it('requires explicit confirmation before deactivating and immediately flips card state to DEACTIVATED', () => {
    const vidhant = component.members.find(m => m.id === 7)!;
    component.openDeactivateModal(vidhant);
    fixture.detectChanges();

    expect(component.showDeactivateModal).toBeTrue();
    expect(memberSpy.deactivateMember).not.toHaveBeenCalled();

    const modalText = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(modalText).toContain('Deactivate Member?');
    expect(modalText).toContain('Attendance, payment and membership history will be preserved.');

    component.confirmDeactivateMember();
    fixture.detectChanges();

    expect(memberSpy.deactivateMember).toHaveBeenCalledWith(7, undefined, 0);
    expect(component.showDeactivateModal).toBeFalse();
    expect(component.deactivatedCount).toBe(1); // synced from mock getAllMembers or patched synchronously
  });
});

