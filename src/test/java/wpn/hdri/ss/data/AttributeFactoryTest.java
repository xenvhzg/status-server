package wpn.hdri.ss.data;

import org.junit.Test;

import java.math.BigDecimal;

import static junit.framework.Assert.assertTrue;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 14.06.13
 */
public class AttributeFactoryTest {
    private AttributeFactory instance = new AttributeFactory();

    @Test
    public void testCreateAttribute_primitive() throws Exception {
        Attribute result = instance.createAttribute("test-attr",null, "test", Interpolation.LAST, BigDecimal.ZERO, float.class, false, new AttributeValuesStorageFactory(".",10,5));

        assertTrue(NumericAttribute.class == result.getClass());
    }

    @Test
    public void testCreateAttribute_Numeric() throws Exception {
        Attribute result = instance.createAttribute("test-attr",null, "test", Interpolation.LAST, BigDecimal.ZERO, Float.class, false, new AttributeValuesStorageFactory(".",10,5));

        assertTrue(NumericAttribute.class == result.getClass());
    }

    @Test
    public void testCreateAttribute_NonNumeric() throws Exception {
        Attribute result = instance.createAttribute("test-attr",null, "test", Interpolation.LAST, BigDecimal.ZERO, String.class, false, new AttributeValuesStorageFactory(".",10,5));

        assertTrue(NonNumericAttribute.class == result.getClass());
    }
}
