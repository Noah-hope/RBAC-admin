package com.adgn.catalogfixture;

import com.adgn.backend.query.management.ManagementInput;
import com.adgn.backend.identity.security.IdentityFailure;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ManagementInputTest {
    private void invalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(IdentityFailure.class, e -> {
            assertThat(e.status()).isEqualTo(422);
            assertThat(e.code()).isEqualTo("VALIDATION_ERROR");
        });
    }

    @Test void namesUseUnicodeCodePointsAndTrimOnlyOuterWhitespace() {
        assertThat(ManagementInput.text("  人😀 名  ", 4, 4)).isEqualTo("人😀 名");
        invalid(() -> ManagementInput.text(null, 1, 4));
        invalid(() -> ManagementInput.text("   ", 1, 4));
        invalid(() -> ManagementInput.text("人😀名字", 1, 3));
        assertThat(ManagementInput.identifier(" a_B-9 ")).isEqualTo("a_B-9");
        for (String value : new String[]{"_a", "部门", "a/b", "a".repeat(65)})
            invalid(() -> ManagementInput.identifier(value));
    }

    @Test void versionsAllowExactSafeIntegerBoundariesAndRejectOverflow() {
        assertThat(ManagementInput.version(0L)).isZero();
        assertThat(ManagementInput.version(9007199254740991L)).isEqualTo(9007199254740991L);
        invalid(() -> ManagementInput.version(null));
        invalid(() -> ManagementInput.version(-1L));
        invalid(() -> ManagementInput.version(9007199254740992L));
    }

    @Test void paginationCalculatesLiteralOffsetsAndRefusesOverflowOrAmbiguousNumbers() {
        assertThat(ManagementInput.pagination(null, null)).isEqualTo(new ManagementInput.Pagination(1, 20, 0));
        assertThat(ManagementInput.pagination("3", "50")).isEqualTo(new ManagementInput.Pagination(3, 50, 100));
        for (String value : new String[]{"0", "-1", "+1", "1.0", "", " 1", "9223372036854775808"})
            invalid(() -> ManagementInput.pagination(value, "10"));
        invalid(() -> ManagementInput.pagination("9223372036854775807", "100"));
        invalid(() -> ManagementInput.pagination("1", "15"));
    }

    @Test void uuidInputRequiresCompleteCanonicalFormAndBooleanIsCaseSensitive() {
        String uuid = "123e4567-e89b-12d3-a456-426614174000";
        assertThat(ManagementInput.key(uuid.toUpperCase())).isEqualTo(UUID.fromString(uuid));
        for (String value : new String[]{null, "", "1-1-1-1-1", "not-a-uuid"}) invalid(() -> ManagementInput.key(value));
        assertThat(ManagementInput.bool(null)).isNull();
        assertThat(ManagementInput.bool("true")).isTrue();
        assertThat(ManagementInput.bool("false")).isFalse();
        for (String value : new String[]{"TRUE", "False", "1", " true", ""}) invalid(() -> ManagementInput.bool(value));
    }

    @Test void searchPrefixEscapesUserWildcardAndEscapeCharacters() {
        assertThat(ManagementInput.like("50%_A=B")).isEqualTo("50=%=_A==B%");
        assertThat(ManagementInput.like("张三")).isEqualTo("张三%");
    }
}
