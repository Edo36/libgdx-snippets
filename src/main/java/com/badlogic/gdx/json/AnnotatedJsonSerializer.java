package com.badlogic.gdx.json;

import com.badlogic.gdx.json.annotations.*;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.reflect.*;

import java.util.Map;

/**
 * Generic implementation of {@link com.badlogic.gdx.utils.Json.Serializer} which uses reflection information
 * to decide which fields to serialize.
 *
 * @see {@link com.badlogic.gdx.json.annotations.JsonSerializable}
 * @see {@link com.badlogic.gdx.json.annotations.JsonSerialize}
 */
public class AnnotatedJsonSerializer<T> implements Json.Serializer<T> {

	private static class FieldAdapter {

		Field field;
		JsonSerialize annotation;
		Json.Serializer<?> serializer;

		FieldAdapter(Field field) {
			this.field = field;
			annotation = field.getDeclaredAnnotation(JsonSerialize.class).getAnnotation(JsonSerialize.class);
		}

		@SuppressWarnings("unchecked")
		<T, V> V get(T instance) {
			try {
				return (V) field.get(instance);
			} catch (ReflectionException e) {
				throw new GdxRuntimeException(e);
			}
		}

		<T, V> void set(T instance, V value) {
			try {
				field.set(instance, value);
			} catch (ReflectionException e) {
				throw new GdxRuntimeException(e);
			}
		}

		String getName() {
			String name = annotation.name();
			return name.isEmpty() ? field.getName() : name;
		}
	}

	private Class<T> clazz;
	private Array<FieldAdapter> fieldAdapters = new Array<>();

	public AnnotatedJsonSerializer(Json json, Class<T> clazz) {
		this.clazz = clazz;
		createSerializer(json);
	}

	@Override
	public void write(Json json, T object, Class knownType) {

		json.writeObjectStart();

		fieldAdapters.forEach(adapter -> {

			Class<?> fieldType = adapter.field.getType();

			if (isKnownContainerType(adapter)) {

				if (isArray(adapter)) {
					writeArray(json, object, adapter);
				} else if (isMap(adapter)) {
					writeMap(json, object, adapter);
				}

			} else {

				Object value = adapter.get(object);

				if (fieldType.isArray()) {
					json.writeValue(adapter.getName(), value, fieldType, fieldType.getComponentType());
				} else {
					json.writeValue(adapter.getName(), value, fieldType);
				}
			}
		});

		json.writeObjectEnd();
	}

	private void writeArray(Json json, T object, FieldAdapter adapter) {
		Array<?> array = adapter.get(object);
		JsonArraySerializer<?> serializer = (JsonArraySerializer<?>) adapter.serializer;
		serializer.write(json, array, Array.class);
	}

	private void writeMap(Json json, T object, FieldAdapter adapter) {

		JsonMap map = adapter.annotation.map()[0];

		Class<?> clazz = map.map();
		JsonMapSerializer<?, ?> serializer = (JsonMapSerializer<?, ?>) adapter.serializer;

		if (Map.class.isAssignableFrom(clazz)) {
			Map<?, ?> value = adapter.get(object);
			serializer.write(json, value.entrySet(), Map.Entry::getKey, Map.Entry::getValue);
		} else if (ObjectMap.class.isAssignableFrom(clazz)) {
			ObjectMap<?, ?> value = adapter.get(object);
			serializer.write(json, value.entries(), e -> e.key, e -> e.value);
		}
	}

	@Override
	public T read(Json json, JsonValue jsonData, Class type) {

		try {

			T object = ClassReflection.newInstance(clazz);

			fieldAdapters.forEach(adapter -> {

				Class<?> fieldType = adapter.field.getType();

				if (isKnownContainerType(adapter)) {

					if (isArray(adapter)) {
						readArray(json, jsonData, object, adapter);
					} else if (isMap(adapter)) {
						readMap(json, jsonData, object, adapter);
					}

				} else {

					Class<?> componentType = getFieldComponentType(adapter);
					Object value = json.readValue(adapter.getName(), fieldType, componentType, jsonData);

					if (value == null) {
						// todo: warning
						return;
					}

					adapter.set(object, value);
				}
			});

			return object;

		} catch (ReflectionException e) {
			throw new GdxRuntimeException(e);
		}
	}

	private void readArray(Json json, JsonValue jsonData, T object, FieldAdapter adapter) {

		JsonArraySerializer<?> serializer = (JsonArraySerializer<?>) adapter.serializer;
		Array<?> array = serializer.read(json, jsonData, Array.class);

		if (array == null) {
			// todo: warning
			return;
		}

		adapter.set(object, array);
	}

	private void readMap(Json json, JsonValue jsonData, T object, FieldAdapter adapter) {

		JsonMap map = adapter.annotation.map()[0];

		Class<?> clazz = map.map();
		JsonMapSerializer<?, ?> serializer = (JsonMapSerializer<?, ?>) adapter.serializer;

		if (Map.class.isAssignableFrom(clazz)) {
			Map<?, ?> value = serializer.read(json, jsonData);
			adapter.set(object, value);
		} else if (ObjectMap.class.isAssignableFrom(clazz)) {
			ObjectMap<?, ?> value = serializer.read(json, jsonData, ObjectMap::new, ObjectMap::put);
			adapter.set(object, value);
		}
	}

	private void createSerializer(Json json) {

		if (!ClassReflection.isAnnotationPresent(clazz, JsonSerializable.class)) {
			throw new GdxRuntimeException("Missing @JsonSerializable annotation for '" +
					ClassReflection.getSimpleName(clazz) + "'.");
		}

		Field[] fields = ClassReflection.getFields(clazz);
		createSerializerFields(json, fields);

		// register self to Json instance
		json.setSerializer(clazz, this);
	}

	private void createSerializerFields(Json json, Field[] fields) {

		ArrayUtils.forEach(fields, field -> {

			// skip anything not annotated
			if (!field.isAnnotationPresent(JsonSerialize.class)) {
				return;
			}

			// add adapter
			FieldAdapter adapter = new FieldAdapter(field);
			fieldAdapters.add(adapter);

			// check for known built-in types
			if (isKnownContainerType(adapter)) {

				// this serializer is not registered to the Json instance
				adapter.serializer = addContainerSerializer(adapter);

				// get types of components stored in container
				Class<?>[] componentTypes = getContainerComponentTypes(adapter);

				// if any of those types is annotated, recursively create a serializer for it
				for (Class<?> componentType : componentTypes) {

					if (ClassReflection.isAnnotationPresent(componentType, JsonSerializable.class)) {

						// no reference stored, this is linked to the Json instance
						new AnnotatedJsonSerializer<>(json, componentType);
					}
				}

			} else {

				Class<?> componentType = getFieldComponentType(adapter);
				Json.Serializer<?> existingSerializer = json.getSerializer(componentType);

				if (existingSerializer != null) {

					// there's already a serializer registered
					adapter.serializer = existingSerializer;

				} else {

					// if type of the field is annotated, recursively create a serializer for it
					if (ClassReflection.isAnnotationPresent(componentType, JsonSerializable.class)) {
						adapter.serializer = new AnnotatedJsonSerializer<>(json, componentType);
					}
				}
			}
		});

	}

	private Class<?> getFieldComponentType(FieldAdapter adapter) {
		Class<?> clazz = adapter.field.getType();
		return clazz.isArray() ? clazz.getComponentType() : clazz;
	}

	private boolean isKnownContainerType(FieldAdapter adapter) {
		return isArray(adapter) || isMap(adapter);
	}

	private Class<?>[] getContainerComponentTypes(FieldAdapter adapter) {

		if (isArray(adapter)) {
			return new Class[] { adapter.annotation.array()[0].value() };
		} else if (isMap(adapter)) {
			JsonMap map = adapter.annotation.map()[0];
			return new Class[] { map.key(), map.value() };
		}

		throw new GdxRuntimeException("Unknown container type!");
	}

	private Json.Serializer<?> addContainerSerializer(FieldAdapter adapter) {

		if (isArray(adapter)) {

			JsonArray[] arrays = adapter.annotation.array();

			if (arrays.length != 1) {
				throw new GdxRuntimeException(
						"@JsonSerialize annotation on Array<> requires array() property.");
			}

			JsonArray array = arrays[0];

			return new JsonArraySerializer<>(adapter.getName(), array);

		} else if (isMap(adapter)) {

			JsonMap map = adapter.annotation.map()[0];

			return new JsonMapSerializer<>(adapter.getName(), map);
		}

		throw new GdxRuntimeException("Unknown container type!");
	}

	private boolean isArray(FieldAdapter adapter) {
		return adapter.field.getType().equals(Array.class);
	}

	private boolean isMap(FieldAdapter adapter) {
		return adapter.annotation.map().length > 0;
	}
}