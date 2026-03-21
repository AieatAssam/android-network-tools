package com.example.netswissknife.core.domain

/**
 * Base interface for all use cases in the domain layer.
 *
 * @param Params input type (use [Unit] for no parameters)
 * @param Result output type
 */
interface UseCase<in Params, out Result> {
    suspend operator fun invoke(params: Params): Result
}
