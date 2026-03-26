package com.ail.insurance.quotation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.ail.core.BaseException;
import com.ail.core.Core;
import com.ail.core.PreconditionException;
import com.ail.core.XMLString;
import com.ail.insurance.quotation.PolicyDataImportService.PolicyDataImportArgument;

public class PolicyDataImportServiceTest {

    private PolicyDataImportService sut;
    private Core mockCore;
    private PolicyDataImportArgument mockArgs;

    @Before
    public void setup() {
        mockCore = mock(Core.class);
        mockArgs = mock(PolicyDataImportArgument.class);

        sut = spy(new PolicyDataImportService());
        sut.setCore(mockCore);
        sut.setArgs(mockArgs);

        when(sut.getCore()).thenReturn(mockCore);
        when(mockArgs.getCallersCore()).thenReturn(mockCore);
        when(mockArgs.getPolicyDataArg()).thenReturn(new XMLString("<policy/>"));
        when(mockArgs.getProductTypeIdArg()).thenReturn("TestProduct");
    }

    @Test(expected = PreconditionException.class)
    public void testNullPolicyData() throws BaseException {
        when(mockArgs.getPolicyDataArg()).thenReturn(null);
        sut.invoke();
    }

    @Test(expected = PreconditionException.class)
    public void testNullProductTypeId() throws BaseException {
        when(mockArgs.getProductTypeIdArg()).thenReturn(null);
        sut.invoke();
    }

    @Test(expected = PreconditionException.class)
    public void testEmptyProductTypeId() throws BaseException {
        when(mockArgs.getProductTypeIdArg()).thenReturn("");
        sut.invoke();
    }
}
