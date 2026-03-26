package com.ail.insurance.quotation;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.ail.core.BaseException;
import com.ail.core.Core;
import com.ail.core.PreconditionException;
import com.ail.insurance.policy.AssessmentSheet;
import com.ail.insurance.policy.Marker;
import com.ail.insurance.policy.MarkerResolution;
import com.ail.insurance.policy.MarkerType;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.policy.Section;
import com.ail.insurance.quotation.AutoResolveReferralService.AutoResolveReferralArgument;

public class AutoResolveReferralServiceTest {

    private AutoResolveReferralService sut;
    private Core mockCore;
    private AutoResolveReferralArgument mockArgs;
    private Policy mockPolicy;
    private AssessmentSheet mockAssessmentSheet;

    @Before
    public void setup() {
        mockCore = mock(Core.class);
        mockArgs = mock(AutoResolveReferralArgument.class);
        mockPolicy = mock(Policy.class);
        mockAssessmentSheet = mock(AssessmentSheet.class);

        sut = spy(new AutoResolveReferralService());
        sut.setCore(mockCore);
        sut.setArgs(mockArgs);

        when(sut.getCore()).thenReturn(mockCore);
        when(mockArgs.getPolicyArgRet()).thenReturn(mockPolicy);
        when(mockArgs.getCallersCore()).thenReturn(mockCore);
        when(mockArgs.getTolerancePercentArg()).thenReturn(10.0);
        when(mockArgs.getApprovedExceptionsArg()).thenReturn(new ArrayList<String>());
        when(mockPolicy.getProductTypeId()).thenReturn("TestProduct");
        when(mockPolicy.getAssessmentSheet()).thenReturn(mockAssessmentSheet);
        when(mockPolicy.getSection()).thenReturn(new ArrayList<Section>());

        // Empty markers by default
        when(mockAssessmentSheet.getLinesOfType(Marker.class)).thenReturn(new Hashtable<String, Marker>());
    }

    @Test(expected = PreconditionException.class)
    public void testNullPolicy() throws BaseException {
        when(mockArgs.getPolicyArgRet()).thenReturn(null);
        sut.invoke();
    }

    @Test
    public void testHappyPathNoMarkers() throws BaseException {
        sut.invoke();
        verify(mockArgs).setResolvedCountRet(0);
    }

    @Test
    public void testResolveWithApprovedExceptions() throws BaseException {
        List<String> exceptions = new ArrayList<String>();
        exceptions.add("low risk");
        when(mockArgs.getApprovedExceptionsArg()).thenReturn(exceptions);

        Hashtable<String, Marker> markers = new Hashtable<String, Marker>();
        Marker referMarker = mock(Marker.class);
        when(referMarker.getType()).thenReturn(MarkerType.REFER);
        when(referMarker.getId()).thenReturn("marker1");
        when(referMarker.getReason()).thenReturn("Flagged as low risk area");
        markers.put("marker1", referMarker);

        when(mockAssessmentSheet.getLinesOfType(Marker.class)).thenReturn(markers);
        when(mockAssessmentSheet.findResolutionByMarkerId("marker1")).thenReturn(null);
        when(mockAssessmentSheet.generateLineId()).thenReturn("resolution1");

        sut.invoke();
        verify(mockArgs).setResolvedCountRet(1);
    }

    @Test
    public void testResolveOutOfBoundsWithinTolerance() throws BaseException {
        when(mockArgs.getTolerancePercentArg()).thenReturn(15.0);

        Hashtable<String, Marker> markers = new Hashtable<String, Marker>();
        Marker referMarker = mock(Marker.class);
        when(referMarker.getType()).thenReturn(MarkerType.REFER);
        when(referMarker.getId()).thenReturn("marker1");
        // 1100 exceeded limit of 1000 = 10% excess, within 15% tolerance
        when(referMarker.getReason()).thenReturn("Value 1100 exceeded limit of 1000");
        markers.put("marker1", referMarker);

        when(mockAssessmentSheet.getLinesOfType(Marker.class)).thenReturn(markers);
        when(mockAssessmentSheet.findResolutionByMarkerId("marker1")).thenReturn(null);
        when(mockAssessmentSheet.generateLineId()).thenReturn("resolution1");

        sut.invoke();
        verify(mockArgs).setResolvedCountRet(1);
    }

    @Test
    public void testDoNotResolveOutOfBoundsExceedingTolerance() throws BaseException {
        when(mockArgs.getTolerancePercentArg()).thenReturn(5.0);

        Hashtable<String, Marker> markers = new Hashtable<String, Marker>();
        Marker referMarker = mock(Marker.class);
        when(referMarker.getType()).thenReturn(MarkerType.REFER);
        when(referMarker.getId()).thenReturn("marker1");
        // 1200 exceeded limit of 1000 = 20% excess, beyond 5% tolerance
        when(referMarker.getReason()).thenReturn("Value 1200 exceeded limit of 1000");
        markers.put("marker1", referMarker);

        when(mockAssessmentSheet.getLinesOfType(Marker.class)).thenReturn(markers);
        when(mockAssessmentSheet.findResolutionByMarkerId("marker1")).thenReturn(null);

        sut.invoke();
        verify(mockArgs).setResolvedCountRet(0);
    }

    @Test
    public void testDoNotResolveUnparseableReason() throws BaseException {
        Hashtable<String, Marker> markers = new Hashtable<String, Marker>();
        Marker referMarker = mock(Marker.class);
        when(referMarker.getType()).thenReturn(MarkerType.REFER);
        when(referMarker.getId()).thenReturn("marker1");
        // Unparseable reason - should fail safe and NOT auto-resolve
        when(referMarker.getReason()).thenReturn("Some value exceeded limit of something");
        markers.put("marker1", referMarker);

        when(mockAssessmentSheet.getLinesOfType(Marker.class)).thenReturn(markers);
        when(mockAssessmentSheet.findResolutionByMarkerId("marker1")).thenReturn(null);

        sut.invoke();
        verify(mockArgs).setResolvedCountRet(0);
    }

    @Test
    public void testSkipAlreadyResolvedMarkers() throws BaseException {
        Hashtable<String, Marker> markers = new Hashtable<String, Marker>();
        Marker referMarker = mock(Marker.class);
        when(referMarker.getType()).thenReturn(MarkerType.REFER);
        when(referMarker.getId()).thenReturn("marker1");
        when(referMarker.getReason()).thenReturn("Value exceeded limit of 1000");
        markers.put("marker1", referMarker);

        when(mockAssessmentSheet.getLinesOfType(Marker.class)).thenReturn(markers);
        // Already has a resolution
        when(mockAssessmentSheet.findResolutionByMarkerId("marker1")).thenReturn(mock(MarkerResolution.class));

        sut.invoke();
        verify(mockArgs).setResolvedCountRet(0);
    }

    @Test
    public void testSkipDeclineMarkers() throws BaseException {
        Hashtable<String, Marker> markers = new Hashtable<String, Marker>();
        Marker declineMarker = mock(Marker.class);
        when(declineMarker.getType()).thenReturn(MarkerType.DECLINE);
        when(declineMarker.getId()).thenReturn("marker1");
        markers.put("marker1", declineMarker);

        when(mockAssessmentSheet.getLinesOfType(Marker.class)).thenReturn(markers);

        sut.invoke();
        verify(mockArgs).setResolvedCountRet(0);
    }

    @Test
    public void testDefaultToleranceUsedWhenNull() throws BaseException {
        when(mockArgs.getTolerancePercentArg()).thenReturn(null);
        sut.invoke();
        verify(mockArgs).setResolvedCountRet(0);
    }

    @Test
    public void testDefaultExceptionsUsedWhenNull() throws BaseException {
        when(mockArgs.getApprovedExceptionsArg()).thenReturn(null);
        sut.invoke();
        verify(mockArgs).setResolvedCountRet(0);
    }

    @Test
    public void testSectionLevelSheetProcessing() throws BaseException {
        List<String> exceptions = new ArrayList<String>();
        exceptions.add("minor issue");
        when(mockArgs.getApprovedExceptionsArg()).thenReturn(exceptions);

        // Policy-level sheet has no markers
        when(mockAssessmentSheet.getLinesOfType(Marker.class)).thenReturn(new Hashtable<String, Marker>());

        // Section with a referral marker
        Section mockSection = mock(Section.class);
        AssessmentSheet sectionSheet = mock(AssessmentSheet.class);
        when(mockSection.getAssessmentSheet()).thenReturn(sectionSheet);

        Hashtable<String, Marker> sectionMarkers = new Hashtable<String, Marker>();
        Marker sectionMarker = mock(Marker.class);
        when(sectionMarker.getType()).thenReturn(MarkerType.REFER);
        when(sectionMarker.getId()).thenReturn("smarker1");
        when(sectionMarker.getReason()).thenReturn("Found minor issue in coverage");
        sectionMarkers.put("smarker1", sectionMarker);

        when(sectionSheet.getLinesOfType(Marker.class)).thenReturn(sectionMarkers);
        when(sectionSheet.findResolutionByMarkerId("smarker1")).thenReturn(null);
        when(sectionSheet.generateLineId()).thenReturn("sresolution1");

        List<Section> sections = new ArrayList<Section>();
        sections.add(mockSection);
        when(mockPolicy.getSection()).thenReturn(sections);

        sut.invoke();
        verify(mockArgs).setResolvedCountRet(1);
    }
}
