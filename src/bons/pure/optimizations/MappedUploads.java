package bons.pure.optimizations;
import java.util.List;import java.util.function.Function;
/** Optional bridge: producers retain their Stream path if the arena guard skips. */
public interface MappedUploads { boolean bons$uploadMapped(Object commands,List<?> inputs,Function<?,?> mapper,boolean omitNull); }
