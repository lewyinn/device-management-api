export const isUuid = (value) => (
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
);

export const isPositiveInteger = (value) => {
    const number = Number(value);
    return Number.isInteger(number) && number > 0;
};

export const isValidStatus = (value) => ['active', 'inactive'].includes(value);