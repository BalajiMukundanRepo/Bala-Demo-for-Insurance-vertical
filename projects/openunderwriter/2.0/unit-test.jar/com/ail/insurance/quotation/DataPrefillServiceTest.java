package com.ail.insurance.quotation;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.ail.core.BaseException;
import com.ail.core.Core;
import com.ail.core.PreconditionException;
import com.ail.insurance.policy.Policy;
import com.ail.insurance.quotation.DataPrefillService.DataPrefillArgument;
import com.ail.insurance.quotation.DataPrefillService.DataPrefillCommand;

public class DataPrefillServiceTest {

    private DataPrefillService sut;
    private Core mockCore;
    private DataPrefillArgument mockArgs;
    private Policy mockPolicy;
    private DataPrefillCommand mockPrefillCommand;

    @Before
    public void setup() throws BaseException {
        mockCore = mock(Core.class);
        mockArgs = mock(DataPrefillArgument.class);
        mockPolicy = mock(Policy.class);
        mockPrefillCommand = mock(DataPrefillCommand.class);

        sut = spy(new DataPrefillService());
        sut.setCore(mockCore);
        sut.setArgs(mockArgs);

        when(sut.getCore()).thenReturn(mockCore);
        when(mockArgs.getPolicyArgRet()).thenReturn(mockPolicy);
        when(mockArgs.getDataSourceArg()).thenReturn("TestSource");
        when(mockArgs.getCallersCore()).thenReturn(mockCore);
        when(mockPolicy.getProductTypeId()).thenReturn("TestProduct");

        when(mockCore.newCommand(eq("DataPrefill/TestSource"), eq(DataPrefillCommand.class)))
                .thenReturn(mockPrefillCommand);
        when(mockPrefillCommand.getPolicyArgRet()).thenReturn(mockPolicy);
    }

    @Test(expected = PreconditionException.class)
    public void testNullPolicy() throws BaseException {
        when(mockArgs.getPolicyArgRet()).thenReturn(null);
        sut.invoke();
    }

    @Test(expected = PreconditionException.class)
    public void testNullDataSource() throws BaseException {
        when(mockArgs.getDataSourceArg()).thenReturn(null);
        sut.invoke();
    }

    @Test(expected = PreconditionException.class)
    public void testEmptyDataSource() throws BaseException {
        when(mockArgs.getDataSourceArg()).thenReturn("");
        sut.invoke();
    }

    @Test
    public void testHappyPath() throws BaseException {
        sut.invoke();
        verify(mockPrefillCommand).setPolicyArgRet(eq(mockPolicy));
        verify(mockPrefillCommand).setDataSourceArg(eq("TestSource"));
        verify(mockPrefillCommand).invoke();
        verify(mockArgs).setPolicyArgRet(eq(mockPolicy));
    }

    @Test
    public void testGracefulHandlingOfMissingCommand() throws BaseException {
        // When no product-specific command is configured, throw an exception
        when(mockCore.newCommand(anyString(), eq(DataPrefillCommand.class)))
                .thenThrow(new RuntimeException("No command configured"));

        // Should not throw - gracefully handles missing commands
        sut.invoke();
        verify(mockArgs).setPolicyArgRet(eq(mockPolicy));
    }
}
