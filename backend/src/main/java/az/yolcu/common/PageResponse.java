package az.yolcu.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> items, int page, int size, long total) {
    /** Shared ?page&size guard. The offset must fit an int: Hibernate setFirstResult(int) overflows otherwise (500). */
    public static void check(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw ApiException.validation("page >= 0, size 1..100");
    }

    public static <E, T> PageResponse<T> of(Page<E> p, Function<E, T> mapper) {
        return new PageResponse<>(p.getContent().stream().map(mapper).toList(), p.getNumber(), p.getSize(), p.getTotalElements());
    }
}
